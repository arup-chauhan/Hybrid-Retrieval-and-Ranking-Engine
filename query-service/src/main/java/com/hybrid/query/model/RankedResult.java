package com.hybrid.query.model;

public class RankedResult {
    private String id;
    private String title;
    private double score;
    private double lexicalScore;
    private double semanticScore;

    public RankedResult() {
    }

    public RankedResult(String id, String title, double score, double lexicalScore, double semanticScore) {
        this.id = id;
        this.title = title;
        this.score = score;
        this.lexicalScore = lexicalScore;
        this.semanticScore = semanticScore;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getLexicalScore() {
        return lexicalScore;
    }

    public void setLexicalScore(double lexicalScore) {
        this.lexicalScore = lexicalScore;
    }

    public double getSemanticScore() {
        return semanticScore;
    }

    public void setSemanticScore(double semanticScore) {
        this.semanticScore = semanticScore;
    }
}
