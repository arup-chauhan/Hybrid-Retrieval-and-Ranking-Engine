package com.hybrid.indexing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
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
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", embeddingModel);
        payload.put("input", input == null ? "" : input);

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

        return StreamSupport.stream(embedding.spliterator(), false)
                .filter(JsonNode::isNumber)
                .map(JsonNode::asText)
                .collect(Collectors.joining(",", "[", "]"));
    }
}
