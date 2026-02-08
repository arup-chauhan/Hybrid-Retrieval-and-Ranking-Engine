package com.hybrid.gateway.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallbackController {
    @GetMapping("/fallback")
    public String fallback() {
        return "Service temporarily unavailable. Please try again later.";
    }
}
