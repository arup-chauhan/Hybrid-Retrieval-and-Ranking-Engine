package com.hybrid.aggregation;

import com.hybrid.aggregation.model.AggregatedResult;
import com.hybrid.aggregation.service.AggregationService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AggregationServiceTest {

    private final AggregationService aggregationService = new AggregationService();

    @Test
    void testAggregateMergesAndSortsResultsCorrectly() {
        Map<String, Double> ranking = Map.of("doc1", 0.9, "doc2", 0.7);
        Map<String, Double> vector = Map.of("doc2", 0.8, "doc3", 0.9);

        AggregatedResult result = aggregationService.aggregate(ranking, vector);
        Map<String, Double> merged = result.getResults();

        assertEquals(3, merged.size());
        assertTrue(merged.get("doc2") > merged.get("doc3"));
        assertNotNull(merged.get("doc1"));
    }
}
