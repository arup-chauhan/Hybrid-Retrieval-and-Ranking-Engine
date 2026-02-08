package com.hybrid.ranking;

import com.hybrid.ranking.service.RankingService;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class RankingServiceTests {

    private final RankingService rankingService = new RankingService();

    @Test
    void testRankingOrder() {
        List<Map<String, Object>> docs = List.of(
                Map.of("id", "1", "title", "A", "content", "alpha", "bm25Score", 0.7, "vectorDistance", 0.2),
                Map.of("id", "2", "title", "B", "content", "beta", "bm25Score", 0.5, "vectorDistance", 0.8)
        );
        var ranked = rankingService.rank(docs);
        assertEquals("1", ranked.get(0).getId());
    }
}
