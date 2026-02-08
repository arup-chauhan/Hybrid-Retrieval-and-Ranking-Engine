package com.hybrid.query.service;

public interface SemanticSearchClient {
    String search(String query, Integer topK);
}
