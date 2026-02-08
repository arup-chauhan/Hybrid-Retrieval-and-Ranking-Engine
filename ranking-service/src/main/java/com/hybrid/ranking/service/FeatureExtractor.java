package com.hybrid.ranking.service;

import java.util.Map;

public class FeatureExtractor {
    public Map<String, Double> extractFeatures(Map<String, Object> doc) {
        return Map.of(
                "bm25Score", asDouble(doc.get("bm25Score")),
                "vectorDistance", asDouble(doc.get("vectorDistance"))
        );
    }

    private double asDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }
}
