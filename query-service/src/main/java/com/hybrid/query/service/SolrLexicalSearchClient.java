package com.hybrid.query.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Service
public class SolrLexicalSearchClient implements LexicalSearchClient {

    private final WebClient webClient;
    private final long requestTimeoutMs;

    public SolrLexicalSearchClient(String solrUrl) {
        this(solrUrl, 120L);
    }

    @Autowired
    public SolrLexicalSearchClient(
            @Value("${solr.url}") String solrUrl,
            @Value("${solr.request-timeout-ms:120}") long requestTimeoutMs
    ) {
        this.webClient = WebClient.builder().baseUrl(solrUrl).build();
        this.requestTimeoutMs = Math.max(50L, requestTimeoutMs);
    }

    @Override
    public String search(String query) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/select")
                        .queryParam("q", query)
                        .queryParam("fl", "id,title,score")
                        .queryParam("rows", 50)
                        .queryParam("wt", "json")
                .build())
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofMillis(requestTimeoutMs));
    }

    @Override
    public String facets(String field, int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/select")
                        .queryParam("q", "*:*")
                        .queryParam("rows", 0)
                        .queryParam("wt", "json")
                        .queryParam("facet", "true")
                        .queryParam("facet.field", field)
                        .queryParam("facet.limit", limit)
                .build())
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofMillis(requestTimeoutMs));
    }
}
