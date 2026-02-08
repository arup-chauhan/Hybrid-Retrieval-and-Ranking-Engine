package com.hybrid.ranking.service;

import com.hybrid.ranking.model.RankedDocument;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class RankingService {
    private final FeatureExtractor extractor = new FeatureExtractor();
    private final MLRankerService mlRanker = new MLRankerService();

    public List<RankedDocument> rank(List<Map<String, Object>> docs) {
        List<RankedDocument> ranked = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            Map<String, Double> features = extractor.extractFeatures(doc);
            double score = mlRanker.predictScore(features);
            ranked.add(new RankedDocument(
                    doc.get("id").toString(),
                    doc.get("title").toString(),
                    doc.get("content").toString(),
                    score
            ));
        }
        ranked.sort(Comparator.comparingDouble(RankedDocument::getScore).reversed());
        return ranked;
    }
}
