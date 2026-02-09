package com.hybrid.query.controller;

import com.hybrid.query.model.QueryRequest;
import com.hybrid.query.model.QueryResult;
import com.hybrid.query.service.QueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/search")
@CrossOrigin(origins = "*")
public class QueryController {

    @Autowired
    private QueryService queryService;

    @PostMapping
    public QueryResult search(
            @RequestBody QueryRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId
    ) {
        String effectiveTraceId = (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
        return queryService.executeHybridSearch(request, effectiveTraceId);
    }
}
