package com.hybrid.query.controller;

import com.hybrid.query.service.QueryService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FacetController {

    private final QueryService queryService;

    public FacetController(QueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping(value = "/facets", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getFacets(
            @RequestParam(value = "field", required = false) String field,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return ResponseEntity.ok(queryService.fetchFacets(field, limit));
    }
}
