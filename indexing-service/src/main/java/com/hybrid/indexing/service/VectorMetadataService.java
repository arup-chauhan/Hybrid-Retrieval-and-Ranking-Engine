package com.hybrid.indexing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class VectorMetadataService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${vector.ollama.base-url:http://ollama:11434}")
    private String ollamaBaseUrl;

    @Value("${vector.embedding.model:embeddinggemma}")
    private String embeddingModel;

    @Value("${vector.embedding.fast-validation-mode:false}")
    private boolean fastValidationMode;

    @Value("${vector.embedding.cache.enabled:true}")
    private boolean embeddingCacheEnabled;

    @Value("${vector.embedding.cache.max-entries:10000}")
    private int embeddingCacheMaxEntries;

    private final ConcurrentHashMap<String, String> embeddingCache = new ConcurrentHashMap<>();

    public VectorMetadataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertVector(String documentId, String title, String textForEmbedding) {
        try {
            String vectorLiteral = fetchEmbeddingLiteral(textForEmbedding);
            if (vectorLiteral == null) {
                return;
            }

            jdbcTemplate.update(
                    """
                    INSERT INTO vector_metadata (document_id, title, embedding, updated_at)
                    VALUES (?, ?, CAST(? AS vector), NOW())
                    ON CONFLICT (document_id)
                    DO UPDATE SET
                      title = EXCLUDED.title,
                      embedding = EXCLUDED.embedding,
                      updated_at = NOW()
                    """,
                    documentId,
                    title,
                    vectorLiteral
            );
        } catch (Exception ignored) {
        }
    }

    private String fetchEmbeddingLiteral(String input) throws Exception {
        if (fastValidationMode) {
            return syntheticEmbeddingLiteral(input);
        }

        String normalizedInput = input == null ? "" : input;
        if (embeddingCacheEnabled) {
            String cached = embeddingCache.get(normalizedInput);
            if (cached != null) {
                return cached;
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", embeddingModel);
        payload.put("input", normalizedInput);

        String response = restTemplate.postForObject(
                ollamaBaseUrl + "/api/embed",
                payload,
                String.class
        );
        if (response == null || response.isBlank()) {
            return null;
        }

        JsonNode root = objectMapper.readTree(response);
        JsonNode embedding = root.path("embedding");
        if (!embedding.isArray() || embedding.isEmpty()) {
            JsonNode embeddings = root.path("embeddings");
            if (embeddings.isArray() && !embeddings.isEmpty() && embeddings.get(0).isArray()) {
                embedding = embeddings.get(0);
            }
        }
        if (!embedding.isArray() || embedding.isEmpty()) {
            return null;
        }

        String vectorLiteral = StreamSupport.stream(embedding.spliterator(), false)
                .filter(JsonNode::isNumber)
                .map(JsonNode::asText)
                .collect(Collectors.joining(",", "[", "]"));

        if (embeddingCacheEnabled) {
            if (embeddingCache.size() >= Math.max(100, embeddingCacheMaxEntries)) {
                embeddingCache.clear();
            }
            embeddingCache.put(normalizedInput, vectorLiteral);
        }
        return vectorLiteral;
    }

    private String syntheticEmbeddingLiteral(String input) {
        long seed = (input == null ? "" : input).hashCode();
        StringBuilder sb = new StringBuilder(16_000);
        sb.append('[');
        for (int i = 0; i < 768; i++) {
            seed = (seed * 6364136223846793005L) + 1442695040888963407L;
            double v = (((seed >>> 33) % 2_000_000L) / 1_000_000.0) - 1.0;
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v);
        }
        sb.append(']');
        return sb.toString();
    }
}
