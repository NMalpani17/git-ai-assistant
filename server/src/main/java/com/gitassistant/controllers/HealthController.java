package com.gitassistant.controllers;

import akka.actor.typed.ActorRef;
import com.gitassistant.messages.LoggingMessages;
import com.gitassistant.services.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check and system info controller.
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final ActorRef<LoggingMessages.Command> loggingActor;
    private final MetricsService metricsService;

    private static final Instant START_TIME = Instant.now();

    @GetMapping("/")
    public Map<String, Object> home() {
        loggingActor.tell(LoggingMessages.LogInfo.create(
                "HealthController",
                "Home endpoint accessed"
        ));

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "running");
        info.put("application", "Git AI Assistant");
        info.put("version", "1.0.0");
        info.put("akka", "connected");
        info.put("uptimeSeconds", java.time.Duration.between(START_TIME, Instant.now()).getSeconds());

        return info;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("components", Map.of(
                "akka", Map.of("status", "UP"),
                "postgresql", Map.of("status", "UP"),
                "openai", Map.of("status", "UP")
        ));
        health.put("uptimeSeconds", java.time.Duration.between(START_TIME, Instant.now()).getSeconds());

        return health;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();

        info.put("application", Map.of(
                "name", "Git AI Assistant",
                "version", "1.0.0",
                "description", "AI-powered Git command assistant using Akka actors"
        ));

        info.put("actors", Map.of(
                "LoggingActor", "TELL pattern (fire-and-forget)",
                "LLMActor", "ASK pattern (request-response)",
                "RAGActor", "ASK pattern (vector search)",
                "SafetyActor", "FORWARD pattern (preserving sender)",
                "SessionActor", "Orchestrator (coordinates all actors)"
        ));

        info.put("features", Map.of(
                "naturalLanguageToGit", "Convert plain English to Git commands",
                "safetyDetection", "Detect dangerous Git commands",
                "ragSearch", "Vector similarity search with pgvector",
                "concurrentUsers", "Support for parallel user requests"
        ));

        info.put("endpoints", Map.of(
                "POST /api/git/ask", "Full pipeline: RAG → LLM → Safety",
                "GET /api/git/search", "RAG vector search only",
                "GET /api/git/safety", "Safety check only",
                "POST /api/simulation/run", "Run concurrent user simulation",
                "GET /api/metrics", "View application metrics"
        ));

        return info;
    }
}