package com.hybrid.query.config;

import com.hybrid.query.model.QueryRequest;
import com.hybrid.query.service.LexicalSearchClient;
import com.hybrid.query.service.QueryService;
import com.hybrid.query.service.SemanticSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class QueryWarmupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(QueryWarmupRunner.class);

    private final QueryService queryService;
    private final LexicalSearchClient lexicalSearchClient;
    private final SemanticSearchClient semanticSearchClient;
    private final boolean warmupEnabled;
    private final String warmupQuery;
    private final int warmupTopK;
    private final int warmupAttempts;
    private final long warmupDelayMs;

    public QueryWarmupRunner(
            QueryService queryService,
            LexicalSearchClient lexicalSearchClient,
            SemanticSearchClient semanticSearchClient,
            @Value("${query.warmup.enabled:false}") boolean warmupEnabled,
            @Value("${query.warmup.query:startup warmup probe}") String warmupQuery,
            @Value("${query.warmup.top-k:1}") int warmupTopK,
            @Value("${query.warmup.attempts:2}") int warmupAttempts,
            @Value("${query.warmup.delay-ms:2000}") long warmupDelayMs
    ) {
        this.queryService = queryService;
        this.lexicalSearchClient = lexicalSearchClient;
        this.semanticSearchClient = semanticSearchClient;
        this.warmupEnabled = warmupEnabled;
        this.warmupQuery = warmupQuery;
        this.warmupTopK = Math.max(1, warmupTopK);
        this.warmupAttempts = Math.max(1, warmupAttempts);
        this.warmupDelayMs = Math.max(0L, warmupDelayMs);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!warmupEnabled) {
            return;
        }

        QueryRequest request = new QueryRequest();
        request.setQuery(warmupQuery);
        request.setTopK(warmupTopK);

        for (int attempt = 1; attempt <= warmupAttempts; attempt++) {
            try {
                lexicalSearchClient.search(warmupQuery);
                semanticSearchClient.search(warmupQuery, warmupTopK);
                queryService.executeHybridSearch(request, "startup-warmup-" + attempt);
                log.info("query warmup completed attempt={} top_k={}", attempt, warmupTopK);
            } catch (Exception ex) {
                log.warn("query warmup attempt={} failed: {}", attempt, ex.getMessage());
            }
            if (attempt < warmupAttempts && warmupDelayMs > 0) {
                try {
                    Thread.sleep(warmupDelayMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
