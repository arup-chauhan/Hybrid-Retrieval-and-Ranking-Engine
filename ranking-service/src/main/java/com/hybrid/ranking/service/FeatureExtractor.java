package com.hybrid.ranking.service;

import java.util.Map;

public class FeatureExtractor {
    public Map<String, Double> extractFeatures(Map<String, Object> doc) {
        return Map.of(
                "bm25Score", (Double) doc.getOrDefault("bm25Score", 0.0),
                "vectorDistance", (Double) doc.getOrDefault("vectorDistance", 0.0)
        );
    }
}
