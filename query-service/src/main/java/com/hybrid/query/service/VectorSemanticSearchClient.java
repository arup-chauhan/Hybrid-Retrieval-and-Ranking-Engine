package com.hybrid.query.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hybrid.vector.contract.VectorHit;
import com.hybrid.vector.contract.VectorSearchRequest;
import com.hybrid.vector.contract.VectorSearchResponse;
import com.hybrid.vector.contract.VectorSearchServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

@Service
public class VectorSemanticSearchClient implements SemanticSearchClient, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(VectorSemanticSearchClient.class);
    private final WebClient webClient;
    private final boolean grpcEnabled;
    private final ManagedChannel grpcChannel;
    private final VectorSearchServiceGrpc.VectorSearchServiceBlockingStub vectorSearchStub;
    private final ObjectMapper objectMapper;
    private final long requestTimeoutMs;
    private final long grpcDeadlineMs;

    public VectorSemanticSearchClient(String vectorUrl) {
        this(vectorUrl, false, "vector-service", 9094, 120L, 600L, new ObjectMapper());
    }

    @Autowired
    public VectorSemanticSearchClient(
            @Value("${vector.url}") String vectorUrl,
            @Value("${vector.grpc.enabled:false}") boolean grpcEnabled,
            @Value("${vector.grpc.host:vector-service}") String grpcHost,
            @Value("${vector.grpc.port:9094}") int grpcPort,
            @Value("${vector.request-timeout-ms:120}") long requestTimeoutMs,
            @Value("${vector.grpc.timeout-ms:${vector.request-timeout-ms:120}}") long grpcDeadlineMs,
            ObjectMapper objectMapper
    ) {
        this.webClient = WebClient.builder().baseUrl(vectorUrl).build();
        this.grpcEnabled = grpcEnabled;
        this.objectMapper = objectMapper;
        this.requestTimeoutMs = Math.max(50L, requestTimeoutMs);
        this.grpcDeadlineMs = Math.max(50L, grpcDeadlineMs);
        if (grpcEnabled) {
            this.grpcChannel = ManagedChannelBuilder.forAddress(grpcHost, grpcPort).usePlaintext().build();
            this.vectorSearchStub = VectorSearchServiceGrpc.newBlockingStub(grpcChannel);
        } else {
            this.grpcChannel = null;
            this.vectorSearchStub = null;
        }
    }

    @Override
    public String search(String query, Integer topK) {
        if (grpcEnabled && vectorSearchStub != null) {
            try {
                return grpcSearch(query, topK);
            } catch (Exception ex) {
                log.warn("gRPC vector search failed, falling back to REST", ex);
            }
        }
        return restSearch(query, topK);
    }

    private String restSearch(String query, Integer topK) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/vector/search")
                            .queryParam("query", query)
                            .queryParamIfPresent("topK", java.util.Optional.ofNullable(topK))
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(requestTimeoutMs));
        } catch (Exception ex) {
            log.warn("REST vector search failed, returning empty set", ex);
            return "[]";
        }
    }

    private String grpcSearch(String query, Integer topK) throws JsonProcessingException {
        VectorSearchRequest request = VectorSearchRequest.newBuilder()
                .setQuery(query == null ? "" : query)
                .setTopK(topK == null ? 0 : topK)
                .build();

        VectorSearchResponse response = vectorSearchStub
                .withDeadlineAfter(grpcDeadlineMs, TimeUnit.MILLISECONDS)
                .search(request);
        List<Map<String, Object>> hits = new ArrayList<>();
        for (VectorHit hit : response.getHitsList()) {
            hits.add(Map.of(
                    "documentId", hit.getDocumentId(),
                    "similarityScore", hit.getSimilarityScore(),
                    "title", hit.getTitle()
            ));
        }
        return objectMapper.writeValueAsString(hits);
    }

    @Override
    public void destroy() {
        if (grpcChannel != null) {
            grpcChannel.shutdown();
        }
    }
}
