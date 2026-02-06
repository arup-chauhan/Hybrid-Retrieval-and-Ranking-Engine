package com.hybrid.query.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class VectorClientService {

    private final WebClient webClient;

    public VectorClientService(@Value("${vector.url}") String vectorUrl) {
        this.webClient = WebClient.builder().baseUrl(vectorUrl).build();
    }

    public String search(String query) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/vector/search")
                        .queryParam("query", query)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
