package com.hybrid.ingestion.controller;

import com.hybrid.ingestion.model.DocumentRequest;
import com.hybrid.ingestion.service.IngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<String> ingestDocument(@RequestBody DocumentRequest request) {
        ingestionService.processDocument(request);
        return ResponseEntity.ok("Document ingested successfully");
    }
}
