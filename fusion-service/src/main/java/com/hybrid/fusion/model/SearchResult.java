package com.hybrid.fusion.model;

public class SearchResult {
    private String id;
    private String title;
    private double score;

    public SearchResult(String id, String title, double score) {
        this.id = id;
        this.title = title;
        this.score = score;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
