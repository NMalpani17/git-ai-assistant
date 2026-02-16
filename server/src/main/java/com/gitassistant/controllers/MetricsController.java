package com.gitassistant.controllers;

import com.gitassistant.services.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for viewing application metrics.
 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    /**
     * Get all metrics.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(metricsService.getMetrics());
    }

    /**
     * Reset all metrics.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetMetrics() {
        metricsService.reset();
        return ResponseEntity.ok(Map.of("status", "Metrics reset successfully"));
    }
}