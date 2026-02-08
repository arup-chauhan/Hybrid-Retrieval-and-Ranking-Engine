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
    public List<VectorResult> search(@RequestParam String query) {
        return vectorSearchService.search(query);
    }
}
