package com.hybrid.ranking.model;

public class RankedDocument {
    private String id;
    private String title;
    private String content;
    private double score;

    public RankedDocument(String id, String title, String content, double score) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.score = score;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public double getScore() { return score; }
}
