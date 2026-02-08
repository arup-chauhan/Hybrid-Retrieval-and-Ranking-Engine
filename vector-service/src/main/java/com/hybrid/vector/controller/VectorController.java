package com.hybrid.vector.controller;

import com.hybrid.vector.model.VectorResult;
import com.hybrid.vector.service.VectorSearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vector")
public class VectorController {
    private final VectorSearchService vectorSearchService;

    public VectorController(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @GetMapping("/search")
    public List<VectorResult> search(
            @RequestParam("query") String query,
            @RequestParam(value = "topK", required = false) Integer topK
    ) {
        int resolvedTopK = (topK == null || topK <= 0) ? 10 : topK;
        return vectorSearchService.search(query, resolvedTopK);
    }
}
