package com.hybrid.fusion.model;

import java.util.List;

public class FusionResponse {
    private List<SearchResult> results;
    public FusionResponse(List<SearchResult> results) {
        this.results = results;
    }
    public List<SearchResult> getResults() {
        return results;
    }
}
