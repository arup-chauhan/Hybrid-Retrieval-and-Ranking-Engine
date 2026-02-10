package com.hybrid.query.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hybrid.query.model.QueryRequest;
import com.hybrid.query.model.QueryResult;
import com.hybrid.query.model.RankedResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class QueryService {
    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    private static final double LEXICAL_WEIGHT = 0.6;
    private static final double SEMANTIC_WEIGHT = 0.4;
    private static final int DEFAULT_TOP_K = 20;
    private static final long DEFAULT_TOTAL_BUDGET_MS = 120L;
    private static final long DEFAULT_VECTOR_STAGE_BUDGET_MS = 40L;
    private static final long MIN_STAGE_BUDGET_MS = 25L;
    private static final String MODE_HYBRID = "hybrid";
    private static final String MODE_LEXICAL = "lexical";
    private static final String MODE_SEMANTIC = "semantic";
    private static final String FILTER_NONE = "none";
    private static final String FILTER_SOLR = "solr";
    private static final String FILTER_VECTOR = "vector";
    private static final String DEFAULT_QUERY_MODE = MODE_HYBRID;
    private static final String EMPTY_SOLR_RESPONSE = "{\"response\":{\"docs\":[]}}";
    private static final String EMPTY_VECTOR_RESPONSE = "[]";

    private final LexicalSearchClient lexicalSearchClient;
    private final SemanticSearchClient semanticSearchClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final QueryLogService queryLogService;
    private final HybridQueryCacheService queryCacheService;
    private final RedisQueryCacheClient redisQueryCacheClient;
    private final long totalBudgetMs;
    private final long vectorStageBudgetMs;
    private final long lexicalStageBudgetMs;
    private final ConcurrentHashMap<String, Object> inFlightLocks = new ConcurrentHashMap<>();

    public QueryService(
            LexicalSearchClient lexicalSearchClient,
            SemanticSearchClient semanticSearchClient,
            ObjectMapper objectMapper
    ) {
        this(
                lexicalSearchClient,
                semanticSearchClient,
                objectMapper,
                null,
                null,
                null,
                null,
                DEFAULT_TOTAL_BUDGET_MS,
                DEFAULT_VECTOR_STAGE_BUDGET_MS
        );
    }

    public QueryService(
            LexicalSearchClient lexicalSearchClient,
            SemanticSearchClient semanticSearchClient,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this(
                lexicalSearchClient,
                semanticSearchClient,
                objectMapper,
                meterRegistry,
                null,
                null,
                null,
                DEFAULT_TOTAL_BUDGET_MS,
                DEFAULT_VECTOR_STAGE_BUDGET_MS
        );
    }

    public QueryService(
            LexicalSearchClient lexicalSearchClient,
            SemanticSearchClient semanticSearchClient,
            ObjectMapper objectMapper,
            long totalBudgetMs,
            long vectorStageBudgetMs
    ) {
        this(
                lexicalSearchClient,
                semanticSearchClient,
                objectMapper,
                null,
                null,
                null,
                null,
                totalBudgetMs,
                vectorStageBudgetMs
        );
    }

    @Autowired
    public QueryService(
            LexicalSearchClient lexicalSearchClient,
            SemanticSearchClient semanticSearchClient,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            QueryLogService queryLogService,
            HybridQueryCacheService queryCacheService,
            RedisQueryCacheClient redisQueryCacheClient,
            @Value("${query.execution.total-budget-ms:350}") long totalBudgetMs,
            @Value("${query.execution.vector-stage-budget-ms:120}") long vectorStageBudgetMs
    ) {
        this.lexicalSearchClient = lexicalSearchClient;
        this.semanticSearchClient = semanticSearchClient;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.queryLogService = queryLogService;
        this.queryCacheService = queryCacheService;
        this.redisQueryCacheClient = redisQueryCacheClient;
        BudgetConfig config = computeBudgetConfig(totalBudgetMs, vectorStageBudgetMs);
        this.totalBudgetMs = config.totalBudgetMs();
        this.vectorStageBudgetMs = config.vectorBudgetMs();
        this.lexicalStageBudgetMs = config.lexicalBudgetMs();
    }

    public QueryResult executeHybridSearch(QueryRequest request) {
        String generatedTraceId = UUID.randomUUID().toString();
        return executeHybridSearch(request, generatedTraceId);
    }

    public QueryResult executeHybridSearch(QueryRequest request, String traceId) {
        String effectiveTraceId = (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
        long totalStart = System.nanoTime();
        String query = request == null ? null : request.getQuery();
        int topK = resolveTopK(request);
        QueryMode resolvedMode = resolveMode(request == null ? null : request.getMode());
        ResultFilter resolvedFilter = resolveFilter(request == null ? null : request.getFilter());
        log.info("trace_id={} event=query_start query=\"{}\" top_k={}", effectiveTraceId, sanitizeForLog(query), topK);

        QueryResult cached = redisQueryCacheClient == null ? null :
                redisQueryCacheClient.get(query, topK, resolvedMode.label(), resolvedFilter.label());
        if (cached != null) {
            incrementCounter("query_result_redis_cache_hit_total");
            recordQueryLog(query, topK, totalStart, "CACHE_HIT_REDIS");
            log.info(
                    "trace_id={} event=query_cache_hit layer=redis total_ms={}",
                    effectiveTraceId,
                    elapsedMillis(totalStart)
            );
            return copyResult(cached);
        }
        incrementCounter("query_result_redis_cache_miss_total");

        cached = queryCacheService == null ? null :
                queryCacheService.get(query, topK, resolvedMode.label(), resolvedFilter.label());
        if (cached != null) {
            incrementCounter("query_result_inmemory_cache_hit_total");
            recordQueryLog(query, topK, totalStart, "CACHE_HIT_INMEMORY");
            log.info(
                    "trace_id={} event=query_cache_hit layer=inmemory total_ms={}",
                    effectiveTraceId,
                    elapsedMillis(totalStart)
            );
            return copyResult(cached);
        }
        incrementCounter("query_result_inmemory_cache_miss_total");

        String lockKey = buildCacheKey(query, topK, resolvedMode, resolvedFilter);
        Object lock = inFlightLocks.computeIfAbsent(lockKey, ignored -> new Object());
        try {
            synchronized (lock) {
                cached = redisQueryCacheClient == null ? null :
                        redisQueryCacheClient.get(query, topK, resolvedMode.label(), resolvedFilter.label());
                if (cached != null) {
                    incrementCounter("query_result_redis_cache_hit_total");
                    recordQueryLog(query, topK, totalStart, "CACHE_HIT_REDIS");
                    log.info(
                            "trace_id={} event=query_cache_hit layer=redis total_ms={}",
                            effectiveTraceId,
                            elapsedMillis(totalStart)
                    );
                    return copyResult(cached);
                }

                cached = queryCacheService == null ? null :
                        queryCacheService.get(query, topK, resolvedMode.label(), resolvedFilter.label());
                if (cached != null) {
                    incrementCounter("query_result_inmemory_cache_hit_total");
                    recordQueryLog(query, topK, totalStart, "CACHE_HIT_INMEMORY");
                    log.info(
                            "trace_id={} event=query_cache_hit layer=inmemory total_ms={}",
                            effectiveTraceId,
                            elapsedMillis(totalStart)
                    );
                    return copyResult(cached);
                }

                return executeAndCache(query, topK, totalStart, effectiveTraceId, resolvedMode, resolvedFilter);
            }
        } finally {
            inFlightLocks.remove(lockKey, lock);
        }
    }

    private QueryResult executeAndCache(
            String query,
            int topK,
            long totalStart,
            String effectiveTraceId,
            QueryMode mode,
            ResultFilter filter
    ) {

        TimedSearchResult solrTimed = timedSearch(
                "solr_query_latency_ms",
                () -> lexicalSearchClient.search(query),
                EMPTY_SOLR_RESPONSE,
                lexicalStageBudgetMs
        );
        String solrResponse = solrTimed.payload();
        log.info(
                "trace_id={} stage=lexical_search duration_ms={} outcome={} payload_bytes={}",
                effectiveTraceId,
                solrTimed.durationMs(),
                solrTimed.outcome(),
                payloadSize(solrResponse)
        );

        TimedSearchResult vectorTimed = timedVectorSearch(totalStart, query, topK);
        String vectorResponse = vectorTimed.payload();
        log.info(
                "trace_id={} stage=vector_search duration_ms={} outcome={} payload_bytes={}",
                effectiveTraceId,
                vectorTimed.durationMs(),
                vectorTimed.outcome(),
                payloadSize(vectorResponse)
        );

        long parseStart = System.nanoTime();
        List<DocSignal> lexicalSignals = parseSolrSignals(solrResponse);
        List<DocSignal> semanticSignals = parseVectorSignals(vectorResponse);
        double parseDurationMs = elapsedMillis(parseStart);
        log.info(
                "trace_id={} stage=parse_signals duration_ms={} lexical_docs={} semantic_docs={}",
                effectiveTraceId,
                parseDurationMs,
                lexicalSignals.size(),
                semanticSignals.size()
        );

        long mergeStart = System.nanoTime();
        List<RankedResult> ranked = mergeAndRank(lexicalSignals, semanticSignals, topK, mode, filter);
        recordTimer("ranking_merge_duration_ms", mergeStart);
        double mergeDurationMs = elapsedMillis(mergeStart);
        log.info(
                "trace_id={} stage=fusion_rerank duration_ms={} ranked_docs={}",
                effectiveTraceId,
                mergeDurationMs,
                ranked.size()
        );
        incrementCounter("hybrid_query_count_total");

        QueryResult result = new QueryResult();
        result.setMessage(String.format("Hybrid result from Solr + Vector search [mode=%s filter=%s]", mode.label(), filter.label()));
        result.setSolrResult(solrResponse);
        result.setVectorResult(vectorResponse);
        if (filter == ResultFilter.SOLR_ONLY) {
            result.setVectorResult(EMPTY_VECTOR_RESPONSE);
        } else if (filter == ResultFilter.VECTOR_ONLY) {
            result.setSolrResult(EMPTY_SOLR_RESPONSE);
        }
        result.setRankedResults(ranked);
        if (redisQueryCacheClient != null) {
            redisQueryCacheClient.put(query, topK, mode.label(), filter.label(), copyResult(result));
        }
        if (queryCacheService != null) {
            queryCacheService.put(query, topK, mode.label(), filter.label(), copyResult(result));
        }
        String executionStatus = executionStatus(query, solrTimed.outcome(), vectorTimed.outcome());
        log.info(
                "trace_id={} event=query_complete total_ms={} top_k={} status={}",
                effectiveTraceId,
                elapsedMillis(totalStart),
                topK,
                executionStatus
        );
        recordQueryLog(query, topK, totalStart, executionStatus);
        return result;
    }

    private String buildCacheKey(String query, int topK, QueryMode mode, ResultFilter filter) {
        return (query == null ? "" : query)
                + "::" + topK
                + "::" + mode.label()
                + "::" + filter.label();
    }

    public String fetchFacets(String field, Integer limit) {
        String facetField = (field == null || field.isBlank()) ? "category" : field;
        int facetLimit = (limit == null || limit <= 0) ? 20 : limit;
        return safeSearch(() -> lexicalSearchClient.facets(facetField, facetLimit), "{\"facet_counts\":{\"facet_fields\":{}}}");
    }

    private String safeSearch(UnsafeStringSupplier supplier, String fallback) {
        try {
            String value = supplier.get();
            return value == null ? fallback : value;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private TimedSearchResult timedSearch(
            String metricName,
            UnsafeStringSupplier supplier,
            String fallback,
            long timeoutMs
    ) {
        long start = System.nanoTime();
        String value = fallback;
        String outcome = "SUCCESS";
        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return supplier.get();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            String raw = future.get(Math.max(MIN_STAGE_BUDGET_MS, timeoutMs), TimeUnit.MILLISECONDS);
            value = raw == null ? fallback : raw;
        } catch (TimeoutException ex) {
            outcome = "TIMEOUT";
            incrementCounter("query_stage_timeout_total");
        } catch (ExecutionException ex) {
            outcome = "ERROR";
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            log.warn("query stage failed metric={} cause={}", metricName, cause.toString());
            incrementCounter("query_stage_error_total");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            outcome = "ERROR";
            log.warn("query stage interrupted metric={} cause={}", metricName, ex.toString());
            incrementCounter("query_stage_error_total");
        } catch (Exception ex) {
            outcome = "ERROR";
            log.warn("query stage failed metric={} cause={}", metricName, ex.toString());
            incrementCounter("query_stage_error_total");
        } finally {
            recordTimer(metricName, start);
        }
        return new TimedSearchResult(value, elapsedMillis(start), outcome);
    }

    private TimedSearchResult timedVectorSearch(long totalStart, String query, int topK) {
        long remainingMs = remainingBudgetMs(totalStart);
        if (remainingMs < MIN_STAGE_BUDGET_MS) {
            incrementCounter("vector_stage_skipped_budget_total");
            return new TimedSearchResult("[]", 0.0, "SKIPPED_BUDGET");
        }
        long stageTimeoutMs = Math.min(vectorStageBudgetMs, remainingMs);
        return timedSearch(
                "vector_query_latency_ms",
                () -> semanticSearchClient.search(query, topK),
                EMPTY_VECTOR_RESPONSE,
                stageTimeoutMs
        );
    }

    private List<DocSignal> parseSolrSignals(String solrJson) {
        List<DocSignal> docs = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(solrJson);
            JsonNode arr = root.path("response").path("docs");
            if (!arr.isArray()) {
                return docs;
            }
            int total = arr.size();
            for (int idx = 0; idx < total; idx++) {
                JsonNode node = arr.get(idx);
                String id = node.path("id").asText(null);
                if (id == null || id.isBlank()) {
                    continue;
                }
                String title = extractTitle(node.get("title"));
                if (title.isBlank()) {
                    title = extractTitle(node.get("title_t"));
                }
                double score = extractSolrScore(node.get("score"), idx, total);
                docs.add(new DocSignal(id, title, score));
            }
        } catch (IOException ignored) {
            return docs;
        }
        return docs;
    }

    private static String extractTitle(JsonNode titleNode) {
        if (titleNode == null || titleNode.isNull()) {
            return "";
        }
        if (titleNode.isTextual()) {
            return titleNode.asText("");
        }
        if (titleNode.isArray() && !titleNode.isEmpty()) {
            JsonNode first = titleNode.get(0);
            if (first != null && first.isTextual()) {
                return first.asText("");
            }
        }
        return "";
    }

    private static double extractSolrScore(JsonNode scoreNode, int index, int total) {
        if (scoreNode != null && scoreNode.isNumber()) {
            return scoreNode.asDouble();
        }
        if (scoreNode != null && scoreNode.isTextual()) {
            try {
                return Double.parseDouble(scoreNode.asText());
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(1.0, (double) (total - index));
    }

    private List<DocSignal> parseVectorSignals(String vectorJson) {
        List<DocSignal> docs = new ArrayList<>();
        try {
            JsonNode arr = objectMapper.readTree(vectorJson);
            if (!arr.isArray()) {
                return docs;
            }
            for (JsonNode node : arr) {
                JsonNode idNode = node.get("documentId");
                JsonNode scoreNode = node.get("similarityScore");

                if (idNode == null || idNode.isNull() || idNode.asText().isBlank()) {
                    continue;
                }
                if (scoreNode == null || scoreNode.isNull() || !scoreNode.isNumber()) {
                    continue;
                }

                String id = idNode.asText();
                String title = node.path("title").asText("");
                double score = scoreNode.asDouble();
                docs.add(new DocSignal(id, title, score));
            }
        } catch (IOException ignored) {
            return docs;
        }
        return docs;
    }

    private List<RankedResult> mergeAndRank(
            List<DocSignal> lexical,
            List<DocSignal> semantic,
            int topK,
            QueryMode mode,
            ResultFilter filter
    ) {
        Map<String, MergedSignal> merged = new HashMap<>();

        List<DocSignal> lexicalStage = filter == ResultFilter.VECTOR_ONLY ? Collections.emptyList() : lexical;
        List<DocSignal> semanticStage = filter == ResultFilter.SOLR_ONLY ? Collections.emptyList() : semantic;
        double maxLexical = lexicalStage.stream().mapToDouble(DocSignal::score).max().orElse(1.0);
        double maxSemantic = semanticStage.stream().mapToDouble(DocSignal::score).max().orElse(1.0);

        for (DocSignal d : lexicalStage) {
            MergedSignal m = merged.computeIfAbsent(d.id(), id -> new MergedSignal(id));
            m.title = chooseTitle(m.title, d.title());
            m.lexicalScore = normalize(d.score(), maxLexical);
        }

        for (DocSignal d : semanticStage) {
            MergedSignal m = merged.computeIfAbsent(d.id(), id -> new MergedSignal(id));
            m.title = chooseTitle(m.title, d.title());
            m.semanticScore = normalize(d.score(), maxSemantic);
        }

        List<RankedResult> ranked = new ArrayList<>();
        for (MergedSignal m : merged.values()) {
            double fused = (LEXICAL_WEIGHT * m.lexicalScore) + (SEMANTIC_WEIGHT * m.semanticScore);
            if (filter == ResultFilter.SOLR_ONLY) {
                fused = m.lexicalScore;
            } else if (filter == ResultFilter.VECTOR_ONLY) {
                fused = m.semanticScore;
            }
            ranked.add(new RankedResult(m.id, m.title, fused, m.lexicalScore, m.semanticScore));
        }

        Comparator<RankedResult> comparator = comparatorForMode(mode);
        boolean hasLexicalScore = ranked.stream().anyMatch(r -> r.getLexicalScore() > 0);
        boolean hasSemanticScore = ranked.stream().anyMatch(r -> r.getSemanticScore() > 0);
        if (mode == QueryMode.LEXICAL && !hasLexicalScore) {
            comparator = Comparator.comparingDouble(RankedResult::getScore).reversed();
        } else if (mode == QueryMode.SEMANTIC && !hasSemanticScore) {
            comparator = Comparator.comparingDouble(RankedResult::getScore).reversed();
        }
        ranked.sort(comparator);
        if (ranked.size() <= topK) {
            return ranked;
        }
        return new ArrayList<>(ranked.subList(0, topK));
    }

    private Comparator<RankedResult> comparatorForMode(QueryMode mode) {
        switch (mode) {
            case LEXICAL:
                return Comparator.comparingDouble(RankedResult::getLexicalScore).reversed();
            case SEMANTIC:
                return Comparator.comparingDouble(RankedResult::getSemanticScore).reversed();
            default:
                return Comparator.comparingDouble(RankedResult::getScore).reversed();
        }
    }

    private static int resolveTopK(QueryRequest request) {
        if (request == null || request.getTopK() == null || request.getTopK() <= 0) {
            return DEFAULT_TOP_K;
        }
        return request.getTopK();
    }

    private static QueryMode resolveMode(String mode) {
        if (mode == null) {
            return QueryMode.HYBRID;
        }
        switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case MODE_LEXICAL:
                return QueryMode.LEXICAL;
            case MODE_SEMANTIC:
                return QueryMode.SEMANTIC;
            default:
                return QueryMode.HYBRID;
        }
    }

    private static ResultFilter resolveFilter(String filter) {
        if (filter == null) {
            return ResultFilter.NONE;
        }
        switch (filter.trim().toLowerCase(Locale.ROOT)) {
            case FILTER_SOLR:
                return ResultFilter.SOLR_ONLY;
            case FILTER_VECTOR:
                return ResultFilter.VECTOR_ONLY;
            default:
                return ResultFilter.NONE;
        }
    }

    private static double normalize(double value, double max) {
        if (max <= 0) {
            return 0.0;
        }
        double normalized = value / max;
        if (normalized < 0) {
            return 0.0;
        }
        return Math.min(normalized, 1.0);
    }

    private static String chooseTitle(String current, String candidate) {
        if (current != null && !current.isBlank()) {
            return current;
        }
        return candidate == null ? "" : candidate;
    }

    private void recordTimer(String metricName, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.timer(metricName).record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    private void incrementCounter(String metricName) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(metricName).increment();
    }

    private void recordQueryLog(String query, int topK, long startNanos, String status) {
        if (queryLogService == null) {
            return;
        }
        double latencyMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        queryLogService.write(query, topK, latencyMs, status);
    }

    private static String statusForQuery(String query) {
        if (query == null || query.isBlank()) {
            return "EMPTY_QUERY";
        }
        return "SUCCESS";
    }

    private static String executionStatus(String query, String lexicalOutcome, String vectorOutcome) {
        if (query == null || query.isBlank()) {
            return "EMPTY_QUERY";
        }
        if ("TIMEOUT".equals(lexicalOutcome)) {
            return "PARTIAL_LEXICAL_TIMEOUT";
        }
        if ("TIMEOUT".equals(vectorOutcome) || "SKIPPED_BUDGET".equals(vectorOutcome)) {
            return "PARTIAL_VECTOR_TIMEOUT";
        }
        if ("ERROR".equals(lexicalOutcome) || "ERROR".equals(vectorOutcome)) {
            return "PARTIAL_DOWNSTREAM_ERROR";
        }
        return statusForQuery(query);
    }

    private static String sanitizeForLog(String query) {
        if (query == null) {
            return "";
        }
        String trimmed = query.trim().replaceAll("\\s+", " ");
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
    }

    private static int payloadSize(String payload) {
        return payload == null ? 0 : payload.length();
    }

    private static double elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    private long remainingBudgetMs(long totalStartNanos) {
        double elapsedMs = elapsedMillis(totalStartNanos);
        return Math.max(0L, totalBudgetMs - (long) elapsedMs);
    }

    private static QueryResult copyResult(QueryResult source) {
        QueryResult target = new QueryResult();
        target.setMessage(source.getMessage());
        target.setSolrResult(source.getSolrResult());
        target.setVectorResult(source.getVectorResult());
        List<RankedResult> rankedCopy = new ArrayList<>();
        if (source.getRankedResults() != null) {
            for (RankedResult r : source.getRankedResults()) {
                rankedCopy.add(new RankedResult(
                        r.getId(),
                        r.getTitle(),
                        r.getScore(),
                        r.getLexicalScore(),
                        r.getSemanticScore()
                ));
            }
        }
        target.setRankedResults(rankedCopy);
        return target;
    }

    private enum QueryMode {
        HYBRID,
        LEXICAL,
        SEMANTIC;

        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private enum ResultFilter {
        NONE("none"),
        SOLR_ONLY("solr"),
        VECTOR_ONLY("vector");

        private final String label;

        ResultFilter(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private record DocSignal(String id, String title, double score) {
    }

    private record TimedSearchResult(String payload, double durationMs, String outcome) {
    }

    private record BudgetConfig(long totalBudgetMs, long vectorBudgetMs, long lexicalBudgetMs) {
    }

    private static class MergedSignal {
        private final String id;
        private String title = "";
        private double lexicalScore;
        private double semanticScore;

        private MergedSignal(String id) {
            this.id = id;
        }
    }

    private static BudgetConfig computeBudgetConfig(long requestedTotal, long requestedVector) {
        long total = Math.max(100L, requestedTotal);
        long vectorBudget = Math.max(MIN_STAGE_BUDGET_MS, Math.min(requestedVector, total - MIN_STAGE_BUDGET_MS));
        long lexicalBudget = Math.max(MIN_STAGE_BUDGET_MS, total - vectorBudget);
        if (vectorBudget + lexicalBudget > total) {
            lexicalBudget = Math.max(MIN_STAGE_BUDGET_MS, total - vectorBudget);
        }
        return new BudgetConfig(total, vectorBudget, lexicalBudget);
    }

    @FunctionalInterface
    private interface UnsafeStringSupplier {
        String get() throws Exception;
    }
}
