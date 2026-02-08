package com.hybrid.query.model;

import java.util.ArrayList;
import java.util.List;

public class QueryResult {
    private String message;
    private String solrResult;
    private String vectorResult;
    private List<RankedResult> rankedResults = new ArrayList<>();

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSolrResult() {
        return solrResult;
    }

    public void setSolrResult(String solrResult) {
        this.solrResult = solrResult;
    }

    public String getVectorResult() {
        return vectorResult;
    }

    public void setVectorResult(String vectorResult) {
        this.vectorResult = vectorResult;
    }

    public List<RankedResult> getRankedResults() {
        return rankedResults;
    }

    public void setRankedResults(List<RankedResult> rankedResults) {
        this.rankedResults = rankedResults;
    }
}
