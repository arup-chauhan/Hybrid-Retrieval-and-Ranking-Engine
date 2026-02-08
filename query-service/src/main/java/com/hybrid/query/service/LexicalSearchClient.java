package com.hybrid.query.service;

public interface LexicalSearchClient {
    String search(String query);

    String facets(String field, int limit);
}
