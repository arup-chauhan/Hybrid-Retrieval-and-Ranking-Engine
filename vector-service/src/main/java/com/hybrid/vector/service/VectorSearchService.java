package com.hybrid.vector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hybrid.vector.model.VectorResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);
    private static final int DEFAULT_TOP_K = 10;

    private JdbcTemplate jdbcTemplate;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private String ollamaBaseUrl;
    private String embeddingModel;
    private MeterRegistry meterRegistry;
    private boolean embeddingCacheEnabled;
    private long embeddingCacheTtlMillis;
    private int embeddingCacheMaxEntries;
    private final ConcurrentHashMap<String, CachedEmbedding> embeddingCache;

    public VectorSearchService() {
        this.jdbcTemplate = null;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.ollamaBaseUrl = "http://ollama:11434";
        this.embeddingModel = "embeddinggemma";
        this.meterRegistry = null;
        this.embeddingCacheEnabled = true;
        this.embeddingCacheTtlMillis = 10 * 60 * 1000L;
        this.embeddingCacheMaxEntries = 5_000;
        this.embeddingCache = new ConcurrentHashMap<>();
    }

    @Autowired
    public VectorSearchService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${vector.ollama.base-url}") String ollamaBaseUrl,
            @Value("${vector.embedding.model}") String embeddingModel,
            @Value("${vector.embedding-cache.enabled:true}") boolean embeddingCacheEnabled,
            @Value("${vector.embedding-cache.ttl-seconds:600}") long embeddingCacheTtlSeconds,
            @Value("${vector.embedding-cache.max-entries:5000}") int embeddingCacheMaxEntries,
            MeterRegistry meterRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.embeddingModel = embeddingModel;
        this.embeddingCacheEnabled = embeddingCacheEnabled;
        this.embeddingCacheTtlMillis = Math.max(1L, embeddingCacheTtlSeconds) * 1000L;
        this.embeddingCacheMaxEntries = Math.max(100, embeddingCacheMaxEntries);
        this.embeddingCache = new ConcurrentHashMap<>();
        this.meterRegistry = meterRegistry;
    }

    public List<VectorResult> search(String query, int topK) {
        int resolvedTopK = topK <= 0 ? DEFAULT_TOP_K : topK;
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // Keeps unit tests deterministic without external dependencies.
        if (jdbcTemplate == null) {
            if (meterRegistry != null) {
                meterRegistry.counter("vector_query_count_total", "status", "mock").increment();
            }
            return mockResults(resolvedTopK);
        }

        try {
            long embeddingStart = System.nanoTime();
            List<Double> embedding = fetchEmbeddingCached(query);
            recordTimer("vector_embedding_latency_ms", embeddingStart);
            if (embedding.isEmpty()) {
                if (meterRegistry != null) {
                    meterRegistry.counter("vector_query_count_total", "status", "no_embedding").increment();
                }
                return List.of();
            }
            long dbStart = System.nanoTime();
            List<VectorResult> results = queryNearestNeighbors(embedding, resolvedTopK);
            recordTimer("vector_db_latency_ms", dbStart);
            if (meterRegistry != null) {
                meterRegistry.counter("vector_query_count_total", "status", "success").increment();
            }
            return results;
        } catch (Exception ex) {
            if (meterRegistry != null) {
                meterRegistry.counter("vector_query_count_total", "status", "error").increment();
            }
            log.warn("Vector retrieval failed, returning empty result set: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<Double> fetchEmbeddingCached(String query) throws Exception {
        if (!embeddingCacheEnabled) {
            incrementCounter("vector_embedding_cache_miss_total");
            return fetchEmbedding(query);
        }

        long now = System.currentTimeMillis();
        CachedEmbedding cached = embeddingCache.get(query);
        if (cached != null && cached.expiresAtMillis > now) {
            incrementCounter("vector_embedding_cache_hit_total");
            return cached.embedding;
        }

        incrementCounter("vector_embedding_cache_miss_total");
        List<Double> embedding = fetchEmbedding(query);
        if (embedding.isEmpty()) {
            return embedding;
        }

        if (embeddingCache.size() >= embeddingCacheMaxEntries) {
            // Keep eviction simple and bounded for local deployment.
            embeddingCache.clear();
        }
        embeddingCache.put(query, new CachedEmbedding(List.copyOf(embedding), now + embeddingCacheTtlMillis));
        return embedding;
    }

    private List<Double> fetchEmbedding(String query) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", embeddingModel);
        payload.put("input", query);

        String response = restTemplate.postForObject(
                ollamaBaseUrl + "/api/embed",
                payload,
                String.class
        );

        if (response == null || response.isBlank()) {
            return List.of();
        }

        JsonNode root = objectMapper.readTree(response);
        JsonNode embeddingNode = root.path("embedding");
        if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
            // Compatibility path for /api/embed shape: { "embeddings": [[...]] }
            JsonNode embeddingsNode = root.path("embeddings");
            if (embeddingsNode.isArray() && !embeddingsNode.isEmpty() && embeddingsNode.get(0).isArray()) {
                embeddingNode = embeddingsNode.get(0);
            }
        }
        if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
            return List.of();
        }

        List<Double> vector = new ArrayList<>(embeddingNode.size());
        for (JsonNode node : embeddingNode) {
            if (node.isNumber()) {
                vector.add(node.asDouble());
            }
        }
        return vector;
    }

    private List<VectorResult> queryNearestNeighbors(List<Double> embedding, int topK) {
        String vectorLiteral = toVectorLiteral(embedding);
        String sql = """
                SELECT document_id, COALESCE(title, '') AS title,
                       (1 - (embedding <=> CAST(? AS vector))) AS similarity_score
                FROM vector_metadata
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new VectorResult(
                        rs.getString("document_id"),
                        rs.getDouble("similarity_score"),
                        rs.getString("title")
                ),
                vectorLiteral,
                vectorLiteral,
                topK
        );
    }

    private static String toVectorLiteral(List<Double> embedding) {
        return embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private void recordTimer(String metricName, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.timer(metricName).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private void incrementCounter(String metricName) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(metricName).increment();
    }

    private static List<VectorResult> mockResults(int topK) {
        List<VectorResult> results = new ArrayList<>();
        results.add(new VectorResult("doc-001", 0.93, "Mock Doc 1"));
        results.add(new VectorResult("doc-002", 0.89, "Mock Doc 2"));
        return results.subList(0, Math.min(topK, results.size()));
    }

    private static class CachedEmbedding {
        private final List<Double> embedding;
        private final long expiresAtMillis;

        private CachedEmbedding(List<Double> embedding, long expiresAtMillis) {
            this.embedding = embedding;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
