package com.hybrid.vector;

import com.hybrid.vector.model.VectorResult;
import com.hybrid.vector.service.VectorSearchService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VectorSearchServiceTest {

    private static VectorSearchService vectorSearchService;

    @BeforeAll
    static void initService() {
        vectorSearchService = new VectorSearchService();
    }

    @Test
    void searchReturnsResults() {
        List<VectorResult> results = vectorSearchService.search("test query");
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Test
    void searchResultsHaveValidScores() {
        List<VectorResult> results = vectorSearchService.search("semantic");
        for (VectorResult r : results) {
            assertTrue(r.getSimilarityScore() >= 0 && r.getSimilarityScore() <= 1);
            assertNotNull(r.getDocumentId());
        }
    }
}
