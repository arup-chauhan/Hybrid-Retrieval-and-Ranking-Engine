package com.hybrid.query.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hybrid.query.model.QueryRequest;
import com.hybrid.query.model.QueryResult;
import com.hybrid.query.model.RankedResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QueryService {

    private static final double LEXICAL_WEIGHT = 0.6;
    private static final double SEMANTIC_WEIGHT = 0.4;
    private static final int DEFAULT_TOP_K = 20;

    private final LexicalSearchClient lexicalSearchClient;
    private final SemanticSearchClient semanticSearchClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final QueryLogService queryLogService;
    private final HybridQueryCacheService queryCacheService;
    private final RedisQueryCacheClient redisQueryCacheClient;

    public QueryService(
            LexicalSearchClient lexicalSearchClient,
            SemanticSearchClient semanticSearchClient,
            ObjectMapper objectMapper
    ) {
        this(lexicalSearchClient, semanticSearchClient, objectMapper, null, null, null, null);
    }

    public QueryService(
            LexicalSearchClient lexicalSearchClient,
            SemanticSearchClient semanticSearchClient,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this(lexicalSearchClient, semanticSearchClient, objectMapper, meterRegistry, null, null, null);
    }

    @Autowired
    public QueryService(
            LexicalSearchClient lexicalSearchClient,
            SemanticSearchClient semanticSearchClient,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            QueryLogService queryLogService,
            HybridQueryCacheService queryCacheService,
            RedisQueryCacheClient redisQueryCacheClient
    ) {
        this.lexicalSearchClient = lexicalSearchClient;
        this.semanticSearchClient = semanticSearchClient;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.queryLogService = queryLogService;
        this.queryCacheService = queryCacheService;
        this.redisQueryCacheClient = redisQueryCacheClient;
    }

    public QueryResult executeHybridSearch(QueryRequest request) {
        long totalStart = System.nanoTime();
        String query = request == null ? null : request.getQuery();
        int topK = resolveTopK(request);
        QueryResult cached = redisQueryCacheClient == null ? null : redisQueryCacheClient.get(query, topK);
        if (cached != null) {
            incrementCounter("query_result_redis_cache_hit_total");
            recordQueryLog(query, topK, totalStart, "CACHE_HIT_REDIS");
            return copyResult(cached);
        }
        incrementCounter("query_result_redis_cache_miss_total");

        cached = queryCacheService == null ? null : queryCacheService.get(query, topK);
        if (cached != null) {
            incrementCounter("query_result_inmemory_cache_hit_total");
            recordQueryLog(query, topK, totalStart, "CACHE_HIT_INMEMORY");
            return copyResult(cached);
        }
        incrementCounter("query_result_inmemory_cache_miss_total");

        String solrResponse = timedSearch(
                "solr_query_latency_ms",
                () -> lexicalSearchClient.search(query),
                "{\"response\":{\"docs\":[]}}"
        );
        String vectorResponse = timedSearch(
                "vector_query_latency_ms",
                () -> semanticSearchClient.search(query, topK),
                "[]"
        );

        List<DocSignal> lexicalSignals = parseSolrSignals(solrResponse);
        List<DocSignal> semanticSignals = parseVectorSignals(vectorResponse);

        long mergeStart = System.nanoTime();
        List<RankedResult> ranked = mergeAndRank(lexicalSignals, semanticSignals, topK);
        recordTimer("ranking_merge_duration_ms", mergeStart);
        incrementCounter("hybrid_query_count_total");

        QueryResult result = new QueryResult();
        result.setMessage("Hybrid result from Solr + Vector search");
        result.setSolrResult(solrResponse);
        result.setVectorResult(vectorResponse);
        result.setRankedResults(ranked);
        if (redisQueryCacheClient != null) {
            redisQueryCacheClient.put(query, topK, copyResult(result));
        }
        if (queryCacheService != null) {
            queryCacheService.put(query, topK, copyResult(result));
        }
        recordQueryLog(query, topK, totalStart, statusForQuery(query));
        return result;
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

    private String timedSearch(String metricName, UnsafeStringSupplier supplier, String fallback) {
        long start = System.nanoTime();
        try {
            return safeSearch(supplier, fallback);
        } finally {
            recordTimer(metricName, start);
        }
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

    private List<RankedResult> mergeAndRank(List<DocSignal> lexical, List<DocSignal> semantic, int topK) {
        Map<String, MergedSignal> merged = new HashMap<>();

        double maxLexical = lexical.stream().mapToDouble(DocSignal::score).max().orElse(1.0);
        double maxSemantic = semantic.stream().mapToDouble(DocSignal::score).max().orElse(1.0);

        for (DocSignal d : lexical) {
            MergedSignal m = merged.computeIfAbsent(d.id(), id -> new MergedSignal(id));
            m.title = chooseTitle(m.title, d.title());
            m.lexicalScore = normalize(d.score(), maxLexical);
        }

        for (DocSignal d : semantic) {
            MergedSignal m = merged.computeIfAbsent(d.id(), id -> new MergedSignal(id));
            m.title = chooseTitle(m.title, d.title());
            m.semanticScore = normalize(d.score(), maxSemantic);
        }

        List<RankedResult> ranked = new ArrayList<>();
        for (MergedSignal m : merged.values()) {
            double fused = (LEXICAL_WEIGHT * m.lexicalScore) + (SEMANTIC_WEIGHT * m.semanticScore);
            ranked.add(new RankedResult(m.id, m.title, fused, m.lexicalScore, m.semanticScore));
        }

        ranked.sort(Comparator.comparingDouble(RankedResult::getScore).reversed());
        if (ranked.size() <= topK) {
            return ranked;
        }
        return new ArrayList<>(ranked.subList(0, topK));
    }

    private static int resolveTopK(QueryRequest request) {
        if (request == null || request.getTopK() == null || request.getTopK() <= 0) {
            return DEFAULT_TOP_K;
        }
        return request.getTopK();
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

    private record DocSignal(String id, String title, double score) {
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

    @FunctionalInterface
    private interface UnsafeStringSupplier {
        String get() throws Exception;
    }
}
