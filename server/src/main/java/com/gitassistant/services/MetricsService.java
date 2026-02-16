package com.gitassistant.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for collecting and reporting application metrics.
 */
@Service
@Slf4j
public class MetricsService {

    // Request counters
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);

    // Response time tracking (last 1000 requests)
    private final LinkedList<Long> responseTimes = new LinkedList<>();
    private static final int MAX_RESPONSE_TIMES = 1000;

    // Actor-specific metrics
    private final Map<String, AtomicLong> actorRequestCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> actorErrorCounts = new ConcurrentHashMap<>();

    // Endpoint metrics
    private final Map<String, AtomicLong> endpointCounts = new ConcurrentHashMap<>();

    // Start time for uptime calculation
    private final Instant startTime = Instant.now();

    /**
     * Record a request completion.
     */
    public void recordRequest(String endpoint, long responseTimeMs, boolean success) {
        totalRequests.incrementAndGet();

        if (success) {
            successfulRequests.incrementAndGet();
        } else {
            failedRequests.incrementAndGet();
        }

        synchronized (responseTimes) {
            responseTimes.addLast(responseTimeMs);
            if (responseTimes.size() > MAX_RESPONSE_TIMES) {
                responseTimes.removeFirst();
            }
        }

        endpointCounts.computeIfAbsent(endpoint, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Record actor activity.
     */
    public void recordActorRequest(String actorName) {
        actorRequestCounts.computeIfAbsent(actorName, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Record actor error.
     */
    public void recordActorError(String actorName) {
        actorErrorCounts.computeIfAbsent(actorName, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Get comprehensive metrics report.
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // System info
        metrics.put("system", Map.of(
                "uptimeSeconds", java.time.Duration.between(startTime, Instant.now()).getSeconds(),
                "startTime", startTime.toString(),
                "javaVersion", System.getProperty("java.version"),
                "availableProcessors", Runtime.getRuntime().availableProcessors()
        ));

        // Memory info
        Runtime runtime = Runtime.getRuntime();
        metrics.put("memory", Map.of(
                "totalMB", runtime.totalMemory() / (1024 * 1024),
                "freeMB", runtime.freeMemory() / (1024 * 1024),
                "usedMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
                "maxMB", runtime.maxMemory() / (1024 * 1024)
        ));

        // Request metrics
        long total = totalRequests.get();
        long successful = successfulRequests.get();
        long failed = failedRequests.get();
        double successRate = total > 0 ? (successful * 100.0 / total) : 0;

        metrics.put("requests", Map.of(
                "total", total,
                "successful", successful,
                "failed", failed,
                "successRate", String.format("%.2f%%", successRate)
        ));

        // Response time metrics
        metrics.put("responseTimes", getResponseTimeMetrics());

        // Actor metrics
        Map<String, Map<String, Long>> actorMetrics = new LinkedHashMap<>();
        for (String actor : actorRequestCounts.keySet()) {
            actorMetrics.put(actor, Map.of(
                    "requests", actorRequestCounts.getOrDefault(actor, new AtomicLong(0)).get(),
                    "errors", actorErrorCounts.getOrDefault(actor, new AtomicLong(0)).get()
            ));
        }
        metrics.put("actors", actorMetrics);

        // Endpoint metrics
        Map<String, Long> endpoints = new LinkedHashMap<>();
        endpointCounts.forEach((k, v) -> endpoints.put(k, v.get()));
        metrics.put("endpoints", endpoints);

        return metrics;
    }

    /**
     * Get response time statistics.
     */
    private Map<String, Object> getResponseTimeMetrics() {
        List<Long> times;
        synchronized (responseTimes) {
            times = new ArrayList<>(responseTimes);
        }

        if (times.isEmpty()) {
            return Map.of(
                    "count", 0,
                    "avg", 0,
                    "min", 0,
                    "max", 0,
                    "p50", 0,
                    "p90", 0,
                    "p99", 0
            );
        }

        Collections.sort(times);
        double avg = times.stream().mapToLong(Long::longValue).average().orElse(0);

        return Map.of(
                "count", times.size(),
                "avgMs", Math.round(avg),
                "minMs", times.get(0),
                "maxMs", times.get(times.size() - 1),
                "p50Ms", getPercentile(times, 50),
                "p90Ms", getPercentile(times, 90),
                "p99Ms", getPercentile(times, 99)
        );
    }

    private long getPercentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }

    /**
     * Reset all metrics.
     */
    public void reset() {
        totalRequests.set(0);
        successfulRequests.set(0);
        failedRequests.set(0);
        synchronized (responseTimes) {
            responseTimes.clear();
        }
        actorRequestCounts.clear();
        actorErrorCounts.clear();
        endpointCounts.clear();
        log.info("Metrics reset");
    }
}