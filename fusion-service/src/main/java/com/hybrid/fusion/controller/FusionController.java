package com.hybrid.fusion.controller;

import com.hybrid.fusion.model.FusionResponse;
import com.hybrid.fusion.service.FusionService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/fusion")
public class FusionController {
    private final FusionService fusionService;

    public FusionController(FusionService fusionService) {
        this.fusionService = fusionService;
    }

    @PostMapping("/combine")
    public FusionResponse combineResults(@RequestBody Map<String, Object> payload) {
        return fusionService.fuseResults(payload);
    }
}
