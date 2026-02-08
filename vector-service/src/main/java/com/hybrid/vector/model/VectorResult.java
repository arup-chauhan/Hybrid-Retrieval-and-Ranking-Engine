package com.hybrid.vector.model;

public class VectorResult {
    private String documentId;
    private double similarityScore;

    public VectorResult(String documentId, double similarityScore) {
        this.documentId = documentId;
        this.similarityScore = similarityScore;
    }

    public String getDocumentId() { return documentId; }
    public double getSimilarityScore() { return similarityScore; }
}
