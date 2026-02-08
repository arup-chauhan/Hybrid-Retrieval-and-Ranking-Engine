package com.hybrid.ranking.service;

import com.hybrid.ranking.model.RankedDocument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class RankingService {
    private final FeatureExtractor extractor = new FeatureExtractor();
    private final MLRankerService mlRanker = new MLRankerService();

    public List<RankedDocument> rank(List<Map<String, Object>> docs) {
        List<RankedDocument> ranked = new ArrayList<>();
        if (docs == null) {
            return ranked;
        }

        for (Map<String, Object> doc : docs) {
            if (doc == null) {
                continue;
            }

            Map<String, Double> features = extractor.extractFeatures(doc);
            double score = mlRanker.predictScore(features);

            String id = asString(doc.get("id"));
            if (id.isBlank()) {
                continue;
            }

            ranked.add(new RankedDocument(
                    id,
                    asString(doc.get("title")),
                    asString(doc.get("content")),
                    score
            ));
        }

        ranked.sort(Comparator.comparingDouble(RankedDocument::getScore).reversed());
        return ranked;
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }
}
