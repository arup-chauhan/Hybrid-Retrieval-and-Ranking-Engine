package com.hybrid.query.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hybrid.query.model.QueryResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

@Service
public class RedisQueryCacheClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final long ttlSeconds;

    public RedisQueryCacheClient(
            @Value("${caching.url:http://caching-service:8096}") String cachingUrl,
            @Value("${query.cache.enabled:true}") boolean enabled,
            @Value("${query.cache.ttl-seconds:120}") long ttlSeconds,
            ObjectMapper objectMapper
    ) {
        this.webClient = WebClient.builder().baseUrl(cachingUrl).build();
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.ttlSeconds = Math.max(1L, ttlSeconds);
    }

    public QueryResult get(String query, int topK, String mode, String filter) {
        if (!enabled) {
            return null;
        }
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cache/get")
                            .queryParam("key", buildKey(query, topK, mode, filter))
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (response == null || response.isBlank() || "null".equals(response)) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            if (root.has("@class") && root.has("message")) {
                return objectMapper.convertValue(root, QueryResult.class);
            }
            if (root.has("value")) {
                return objectMapper.convertValue(root.path("value"), QueryResult.class);
            }
            return objectMapper.convertValue(root, QueryResult.class);
        } catch (Exception ex) {
            return null;
        }
    }

    public void put(String query, int topK, String mode, String filter, QueryResult result) {
        if (!enabled || result == null) {
            return;
        }
        try {
            webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cache/put")
                            .queryParam("key", buildKey(query, topK, mode, filter))
                            .queryParam("ttl", ttlSeconds)
                            .build())
                    .bodyValue(result)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception ignored) {
        }
    }

    private static String buildKey(String query, int topK, String mode, String filter) {
        String q = Optional.ofNullable(query).orElse("");
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(q.getBytes(StandardCharsets.UTF_8));
        return "hybrid:query:" + encoded
                + ":topk:" + topK
                + ":mode:" + normalize(mode)
                + ":filter:" + normalize(filter);
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
