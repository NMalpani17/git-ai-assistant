package com.gitassistant.controllers;

import com.gitassistant.services.ConcurrentUserSimulator;
import com.gitassistant.services.ConcurrentUserSimulator.SimulationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for running concurrent user simulations and viewing metrics.
 */
@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
@Slf4j
public class SimulationController {

    private final ConcurrentUserSimulator simulator;

    /**
     * Run a concurrent user simulation.
     *
     * @param users Number of concurrent users (default: 5, max: 20)
     * @param requests Requests per user (default: 3, max: 10)
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runSimulation(
            @RequestParam(defaultValue = "5") int users,
            @RequestParam(defaultValue = "3") int requests) {

        // Validate parameters
        users = Math.min(Math.max(users, 1), 20);
        requests = Math.min(Math.max(requests, 1), 10);

        log.info("Starting simulation with {} users, {} requests each", users, requests);

        try {
            SimulationResult result = simulator.runSimulation(users, requests);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("summary", Map.of(
                    "totalUsers", result.numUsers(),
                    "requestsPerUser", result.requestsPerUser(),
                    "totalRequests", result.totalRequests(),
                    "successfulRequests", result.successfulRequests(),
                    "failedRequests", result.failedRequests(),
                    "successRate", String.format("%.1f%%", result.successRate())
            ));
            response.put("timing", Map.of(
                    "totalSimulationTimeMs", result.totalSimulationTimeMs(),
                    "avgResponseTimeMs", Math.round(result.avgResponseTimeMs()),
                    "minResponseTimeMs", result.minResponseTimeMs(),
                    "maxResponseTimeMs", result.maxResponseTimeMs(),
                    "p50ResponseTimeMs", result.p50ResponseTimeMs(),
                    "p90ResponseTimeMs", result.p90ResponseTimeMs(),
                    "p99ResponseTimeMs", result.p99ResponseTimeMs()
            ));
            response.put("throughput", Map.of(
                    "requestsPerSecond", String.format("%.2f", result.throughputPerSecond())
            ));

            // Include per-user results
            response.put("userResults", result.userResults().stream()
                    .map(ur -> Map.of(
                            "userId", ur.userId(),
                            "sessionId", ur.sessionId(),
                            "successful", ur.successfulRequests(),
                            "failed", ur.failedRequests(),
                            "avgResponseTimeMs", Math.round(ur.avgResponseTimeMs())
                    ))
                    .toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Simulation failed: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Quick simulation with default parameters (5 users, 3 requests each).
     */
    @GetMapping("/quick")
    public ResponseEntity<Map<String, Object>> quickSimulation() {
        return runSimulation(5, 3);
    }

    /**
     * Stress test with more users (10 users, 5 requests each).
     */
    @GetMapping("/stress")
    public ResponseEntity<Map<String, Object>> stressTest() {
        return runSimulation(10, 5);
    }

    /**
     * Run simulation asynchronously and return immediately.
     */
    @PostMapping("/run-async")
    public ResponseEntity<Map<String, Object>> runSimulationAsync(
            @RequestParam(defaultValue = "5") int users,
            @RequestParam(defaultValue = "3") int requests) {

        users = Math.min(Math.max(users, 1), 20);
        requests = Math.min(Math.max(requests, 1), 10);

        final int finalUsers = users;
        final int finalRequests = requests;

        // Run simulation in background
        CompletableFuture.runAsync(() -> {
            try {
                SimulationResult result = simulator.runSimulation(finalUsers, finalRequests);
                log.info("Async simulation completed: {}", result);
            } catch (Exception e) {
                log.error("Async simulation failed: {}", e.getMessage(), e);
            }
        });

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", String.format(
                "Simulation started with %d users, %d requests each. Check logs for results.",
                users, requests));

        return ResponseEntity.accepted().body(response);
    }
}