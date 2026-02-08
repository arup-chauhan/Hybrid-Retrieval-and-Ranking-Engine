package com.hybrid.ranking.controller;

import com.hybrid.ranking.model.RankedDocument;
import com.hybrid.ranking.service.RankingService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/rank")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @PostMapping
    public List<RankedDocument> rankDocuments(@RequestBody List<Map<String, Object>> docs) {
        return rankingService.rank(docs);
    }
}
