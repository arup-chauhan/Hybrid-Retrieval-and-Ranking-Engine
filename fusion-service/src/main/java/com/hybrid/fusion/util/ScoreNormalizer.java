package com.hybrid.fusion.util;

public class ScoreNormalizer {
    public static double normalize(double score) {
        return 1 / (1 + Math.exp(-score)); // sigmoid normalization
    }
}
