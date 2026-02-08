package com.hybrid.query.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class QueryLogService {

    private static final Logger log = LoggerFactory.getLogger(QueryLogService.class);

    private final JdbcTemplate jdbcTemplate;

    public QueryLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void write(String query, int topK, double latencyMs, String status) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO query_logs (query_text, top_k, latency_ms, status) VALUES (?, ?, ?, ?)",
                    query == null ? "" : query,
                    topK,
                    latencyMs,
                    status
            );
        } catch (Exception ex) {
            log.debug("query_logs write skipped: {}", ex.getMessage());
        }
    }
}
