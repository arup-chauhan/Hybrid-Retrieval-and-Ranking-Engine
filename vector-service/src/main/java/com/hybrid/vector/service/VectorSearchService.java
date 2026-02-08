package com.hybrid.vector.service;

import com.hybrid.vector.model.VectorResult;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class VectorSearchService {

    public List<VectorResult> search(String query) {
        // Placeholder: Replace with actual embedding + ANN search
        List<VectorResult> results = new ArrayList<>();
        results.add(new VectorResult("doc-001", 0.93));
        results.add(new VectorResult("doc-002", 0.89));
        return results;
    }
}
