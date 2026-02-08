package com.hybrid.fusion.service;

import com.hybrid.fusion.model.FusionResponse;
import com.hybrid.fusion.model.SearchResult;
import com.hybrid.fusion.util.ScoreNormalizer;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FusionService {
    public FusionResponse fuseResults(Map<String, Object> payload) {
        List<Map<String, Object>> bm25Results = (List<Map<String, Object>>) payload.get("bm25");
        List<Map<String, Object>> vectorResults = (List<Map<String, Object>>) payload.get("vector");

        List<SearchResult> combined = new ArrayList<>();
        combined.addAll(convertToSearchResults(bm25Results, 0.4));
        combined.addAll(convertToSearchResults(vectorResults, 0.6));

        combined.forEach(r -> r.setScore(ScoreNormalizer.normalize(r.getScore())));
        combined.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        return new FusionResponse(combined);
    }

    private List<SearchResult> convertToSearchResults(List<Map<String, Object>> results, double weight) {
        if (results == null) return Collections.emptyList();
        return results.stream().map(r ->
                new SearchResult(
                        (String) r.get("id"),
                        (String) r.get("title"),
                        ((Number) r.get("score")).doubleValue() * weight
                )
        ).collect(Collectors.toList());
    }
}
