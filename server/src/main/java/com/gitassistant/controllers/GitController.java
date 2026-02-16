package com.gitassistant.controllers;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import com.gitassistant.config.AkkaConfig;
import com.gitassistant.messages.LLMMessages;
import com.gitassistant.messages.LLMMessages.GitQueryResponse;
import com.gitassistant.messages.LoggingMessages;
import com.gitassistant.messages.RAGMessages;
import com.gitassistant.messages.SafetyMessages;
import com.gitassistant.messages.SessionMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * REST Controller for Git AI Assistant.
 * Demonstrates all Akka patterns: TELL, ASK, and FORWARD.
 */
@RestController
@RequestMapping("/api/git")
@RequiredArgsConstructor
@Slf4j
public class GitController {

    private final ActorRef<LLMMessages.Command> llmActor;
    private final ActorRef<LoggingMessages.Command> loggingActor;
    private final ActorRef<RAGMessages.Command> ragActor;
    private final ActorRef<SafetyMessages.Command> safetyActor;
    private final ActorRef<SessionMessages.Command> sessionActor;
    private final ActorSystem<AkkaConfig.GuardianActor.Command> actorSystem;

    @Value("${server.port:8080}")
    private int serverPort;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    // ==================== MAIN PIPELINE ENDPOINTS ====================

    /**
     * FULL PIPELINE - Uses SessionActor to orchestrate RAG → LLM → Safety
     */
    @PostMapping("/ask")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> askFullPipeline(
            @RequestBody Map<String, String> request) {

        String userQuery = request.get("query");
        String sessionId = request.getOrDefault("sessionId", UUID.randomUUID().toString());

        log.info("Full pipeline request: {} (session: {})", userQuery, sessionId);

        return AskPattern.ask(
                        sessionActor,
                        (ActorRef<SessionMessages.UserResponse> replyTo) ->
                                SessionMessages.UserRequest.create(sessionId, userQuery, replyTo),
                        TIMEOUT,
                        actorSystem.scheduler()
                )
                .thenApply(response -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("sessionId", response.getSessionId());
                    result.put("query", response.getUserQuery());
                    result.put("responseTimeMs", response.getResponseTimeMs());
                    result.put("success", response.isSuccess());
                    result.put("nodePort", serverPort);

                    if (response.isSuccess()) {
                        result.put("command", response.getGitCommand());
                        result.put("explanation", response.getExplanation());
                        result.put("safetyLevel", response.getSafetyLevel().name());
                        result.put("warnings", response.getWarnings());
                        result.put("alternatives", response.getAlternatives());
                    } else {
                        result.put("error", response.getErrorMessage());
                    }

                    return ResponseEntity.ok(result);
                })
                .exceptionally(ex -> {
                    log.error("Pipeline error", ex);
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("error", ex.getMessage());
                    return ResponseEntity.internalServerError().body(error);
                })
                .toCompletableFuture();
    }

    @GetMapping("/ask")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> askFullPipelineGet(
            @RequestParam String q) {
        Map<String, String> request = new HashMap<>();
        request.put("query", q);
        return askFullPipeline(request);
    }

    // ==================== DIRECT LLM QUERY ====================

    @PostMapping("/query")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> query(
            @RequestBody Map<String, String> request) {

        String userQuery = request.get("query");
        String sessionId = request.getOrDefault("sessionId", UUID.randomUUID().toString());

        log.info("Direct LLM query: {} (session: {})", userQuery, sessionId);

        loggingActor.tell(LoggingMessages.LogInfo.create(
                "GitController", "Direct query: " + userQuery));

        long startTime = System.currentTimeMillis();

        CompletionStage<GitQueryResponse> responseFuture = AskPattern.ask(
                llmActor,
                (ActorRef<GitQueryResponse> replyTo) -> LLMMessages.GitQueryRequest.create(
                        sessionId, userQuery, null, replyTo),
                TIMEOUT,
                actorSystem.scheduler()
        );

        return responseFuture
                .thenApply(response -> {
                    long responseTime = System.currentTimeMillis() - startTime;
                    Map<String, Object> result = new HashMap<>();
                    result.put("sessionId", sessionId);
                    result.put("query", userQuery);
                    result.put("responseTimeMs", responseTime);

                    if (response.isSuccess()) {
                        result.put("success", true);
                        result.put("command", response.getGitCommand());
                        result.put("explanation", response.getExplanation());
                        result.put("safetyLevel", response.getSafetyLevel().name());
                    } else {
                        result.put("success", false);
                        result.put("error", response.getErrorMessage());
                    }
                    return ResponseEntity.ok(result);
                })
                .exceptionally(ex -> {
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("error", ex.getMessage());
                    return ResponseEntity.internalServerError().body(error);
                })
                .toCompletableFuture();
    }

    @GetMapping("/query")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> queryGet(@RequestParam String q) {
        Map<String, String> request = new HashMap<>();
        request.put("query", q);
        return query(request);
    }

    // ==================== RAG SEARCH ====================

    @GetMapping("/search")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "3") int topK) {

        log.info("RAG search: {}", q);

        return AskPattern.ask(
                        ragActor,
                        (ActorRef<RAGMessages.SearchResponse> replyTo) ->
                                RAGMessages.SearchRequest.create(UUID.randomUUID().toString(), q, topK, replyTo),
                        TIMEOUT,
                        actorSystem.scheduler()
                )
                .thenApply(response -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("query", q);
                    result.put("success", response.isSuccess());
                    if (response.isSuccess()) {
                        result.put("results", response.getResults());
                        result.put("count", response.getResults().size());
                    } else {
                        result.put("error", response.getErrorMessage());
                    }
                    return ResponseEntity.ok(result);
                })
                .exceptionally(ex -> {
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("error", ex.getMessage());
                    return ResponseEntity.internalServerError().body(error);
                })
                .toCompletableFuture();
    }

    // ==================== SAFETY CHECK ====================

    @GetMapping("/safety")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> checkSafety(
            @RequestParam String command) {

        log.info("Safety check: {}", command);

        return AskPattern.ask(
                        safetyActor,
                        (ActorRef<SafetyMessages.SafetyCheckResponse> replyTo) ->
                                SafetyMessages.SafetyCheckRequest.create(
                                        UUID.randomUUID().toString(), command, "direct check", replyTo),
                        TIMEOUT,
                        actorSystem.scheduler()
                )
                .thenApply(response -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("command", command);
                    result.put("safetyLevel", response.getSafetyLevel().name());
                    result.put("approved", response.isApproved());
                    result.put("warnings", response.getWarnings());
                    result.put("alternatives", response.getAlternatives());
                    return ResponseEntity.ok(result);
                })
                .exceptionally(ex -> {
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("error", ex.getMessage());
                    return ResponseEntity.internalServerError().body(error);
                })
                .toCompletableFuture();
    }
}