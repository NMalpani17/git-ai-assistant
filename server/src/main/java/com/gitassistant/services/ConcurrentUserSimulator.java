package com.gitassistant.services;

import com.gitassistant.controllers.GitController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates concurrent users hitting the Git AI Assistant.
 * Demonstrates Akka's ability to handle parallel requests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConcurrentUserSimulator {

    private final GitController gitController;

    // Sample queries that users might ask
    private static final List<String> SAMPLE_QUERIES = List.of(
            "How do I undo my last commit?",
            "I want to push my changes to remote",
            "How to create a new branch?",
            "How do I merge branches?",
            "I need to discard all my local changes",
            "How to see commit history?",
            "I want to stash my changes",
            "How to delete a branch?",
            "How do I revert a commit?",
            "I need to force push my changes",
            "How to clone a repository?",
            "How do I resolve merge conflicts?",
            "I want to squash commits",
            "How to cherry-pick a commit?",
            "How do I see the diff between commits?"
    );

    /**
     * Run simulation with specified number of users and requests per user.
     */
    public SimulationResult runSimulation(int numUsers, int requestsPerUser) {
        log.info("Starting concurrent user simulation: {} users, {} requests each",
                numUsers, requestsPerUser);

        ExecutorService executor = Executors.newFixedThreadPool(numUsers);
        List<Future<UserSimulationResult>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();
        AtomicInteger completedRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);

        // Submit tasks for each simulated user
        for (int userId = 0; userId < numUsers; userId++) {
            final int userNum = userId;
            futures.add(executor.submit(() -> simulateUser(userNum, requestsPerUser)));
        }

        // Collect results
        List<UserSimulationResult> userResults = new ArrayList<>();
        for (Future<UserSimulationResult> future : futures) {
            try {
                UserSimulationResult result = future.get(120, TimeUnit.SECONDS);
                userResults.add(result);
                completedRequests.addAndGet(result.successfulRequests);
                failedRequests.addAndGet(result.failedRequests);
                totalResponseTime.addAndGet(result.totalResponseTimeMs);
            } catch (Exception e) {
                log.error("User simulation failed: {}", e.getMessage());
                failedRequests.addAndGet(requestsPerUser);
            }
        }

        executor.shutdown();
        long totalTime = System.currentTimeMillis() - startTime;

        // Calculate statistics
        SimulationResult result = calculateStatistics(
                userResults, numUsers, requestsPerUser,
                completedRequests.get(), failedRequests.get(),
                totalResponseTime.get(), totalTime
        );

        log.info("Simulation completed: {}", result);
        return result;
    }

    /**
     * Simulate a single user making multiple requests.
     */
    private UserSimulationResult simulateUser(int userId, int numRequests) {
        String sessionId = "user-" + userId + "-" + UUID.randomUUID().toString().substring(0, 8);
        List<Long> responseTimes = new ArrayList<>();
        int successful = 0;
        int failed = 0;
        Random random = new Random();

        log.debug("User {} starting with session {}", userId, sessionId);

        for (int i = 0; i < numRequests; i++) {
            String query = SAMPLE_QUERIES.get(random.nextInt(SAMPLE_QUERIES.size()));
            long requestStart = System.currentTimeMillis();

            try {
                Map<String, String> request = new HashMap<>();
                request.put("query", query);
                request.put("sessionId", sessionId);

                CompletableFuture<ResponseEntity<Map<String, Object>>> future =
                        gitController.askFullPipeline(request);

                ResponseEntity<Map<String, Object>> response = future.get(30, TimeUnit.SECONDS);
                long responseTime = System.currentTimeMillis() - requestStart;
                responseTimes.add(responseTime);

                if (response.getStatusCode().is2xxSuccessful() &&
                        Boolean.TRUE.equals(response.getBody().get("success"))) {
                    successful++;
                    log.debug("User {} request {} succeeded in {}ms: {}",
                            userId, i, responseTime, query);
                } else {
                    failed++;
                    log.warn("User {} request {} failed: {}", userId, i, response.getBody());
                }

                // Small delay between requests to simulate real user behavior
                Thread.sleep(random.nextInt(500) + 100);

            } catch (Exception e) {
                failed++;
                log.error("User {} request {} error: {}", userId, i, e.getMessage());
            }
        }

        long totalTime = responseTimes.stream().mapToLong(Long::longValue).sum();
        double avgTime = responseTimes.isEmpty() ? 0 :
                responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);

        return new UserSimulationResult(userId, sessionId, successful, failed,
                totalTime, avgTime, responseTimes);
    }

    /**
     * Calculate overall simulation statistics.
     */
    private SimulationResult calculateStatistics(
            List<UserSimulationResult> userResults,
            int numUsers, int requestsPerUser,
            int completedRequests, int failedRequests,
            long totalResponseTime, long totalSimulationTime) {

        // Collect all response times
        List<Long> allResponseTimes = new ArrayList<>();
        userResults.forEach(r -> allResponseTimes.addAll(r.responseTimes));
        Collections.sort(allResponseTimes);

        double avgResponseTime = allResponseTimes.isEmpty() ? 0 :
                allResponseTimes.stream().mapToLong(Long::longValue).average().orElse(0);

        long minResponseTime = allResponseTimes.isEmpty() ? 0 : allResponseTimes.get(0);
        long maxResponseTime = allResponseTimes.isEmpty() ? 0 :
                allResponseTimes.get(allResponseTimes.size() - 1);

        // Calculate percentiles
        long p50 = getPercentile(allResponseTimes, 50);
        long p90 = getPercentile(allResponseTimes, 90);
        long p99 = getPercentile(allResponseTimes, 99);

        // Calculate throughput (requests per second)
        double throughput = totalSimulationTime > 0 ?
                (completedRequests + failedRequests) * 1000.0 / totalSimulationTime : 0;

        double successRate = (completedRequests + failedRequests) > 0 ?
                completedRequests * 100.0 / (completedRequests + failedRequests) : 0;

        return new SimulationResult(
                numUsers,
                requestsPerUser,
                numUsers * requestsPerUser,
                completedRequests,
                failedRequests,
                successRate,
                totalSimulationTime,
                avgResponseTime,
                minResponseTime,
                maxResponseTime,
                p50, p90, p99,
                throughput,
                userResults
        );
    }

    private long getPercentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }

    // Result classes
    public record UserSimulationResult(
            int userId,
            String sessionId,
            int successfulRequests,
            int failedRequests,
            long totalResponseTimeMs,
            double avgResponseTimeMs,
            List<Long> responseTimes
    ) {}

    public record SimulationResult(
            int numUsers,
            int requestsPerUser,
            int totalRequests,
            int successfulRequests,
            int failedRequests,
            double successRate,
            long totalSimulationTimeMs,
            double avgResponseTimeMs,
            long minResponseTimeMs,
            long maxResponseTimeMs,
            long p50ResponseTimeMs,
            long p90ResponseTimeMs,
            long p99ResponseTimeMs,
            double throughputPerSecond,
            List<UserSimulationResult> userResults
    ) {
        @Override
        public String toString() {
            return String.format(
                    "SimulationResult{users=%d, requests=%d, success=%.1f%%, " +
                            "avgTime=%.0fms, p50=%dms, p90=%dms, p99=%dms, throughput=%.2f req/s}",
                    numUsers, totalRequests, successRate,
                    avgResponseTimeMs, p50ResponseTimeMs, p90ResponseTimeMs,
                    p99ResponseTimeMs, throughputPerSecond
            );
        }
    }
}