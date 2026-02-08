package com.hybrid.vector.model;

public class VectorResult {
    private String documentId;
    private double similarityScore;
    private String title;

    public VectorResult(String documentId, double similarityScore) {
        this(documentId, similarityScore, "");
    }

    public VectorResult(String documentId, double similarityScore, String title) {
        this.documentId = documentId;
        this.similarityScore = similarityScore;
        this.title = title == null ? "" : title;
    }

    public String getDocumentId() { return documentId; }
    public double getSimilarityScore() { return similarityScore; }
    public String getTitle() { return title; }
}
