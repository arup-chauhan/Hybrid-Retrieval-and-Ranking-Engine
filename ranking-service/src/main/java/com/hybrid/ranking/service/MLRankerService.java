package com.hybrid.ranking.service;

import java.util.Map;

public class MLRankerService {
    public double predictScore(Map<String, Double> features) {
        double bm25 = features.getOrDefault("bm25Score", 0.0);
        double vec = features.getOrDefault("vectorDistance", 0.0);
        return (0.7 * bm25) + (0.3 * (1 - vec)); // simplified weighted model
    }
}
