package com.gitassistant.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.gitassistant.messages.LLMMessages;
import com.gitassistant.messages.LoggingMessages;
import com.gitassistant.messages.RAGMessages;
import com.gitassistant.messages.SafetyMessages;
import com.gitassistant.messages.SessionMessages.*;
import com.gitassistant.services.RAGService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SessionActor - Main orchestrator that coordinates all actors.
 *
 * Demonstrates complete actor workflow:
 * 1. Receives user request
 * 2. ASK RAGActor for context (vector search)
 * 3. ASK LLMActor for Git command (with RAG context)
 * 4. FORWARD to SafetyActor for safety check
 * 5. TELL LoggingActor to log the interaction
 * 6. Reply to original sender with complete response
 */
@Slf4j
public class SessionActor extends AbstractBehavior<Command> {

    // Actor references
    private final ActorRef<RAGMessages.Command> ragActor;
    private final ActorRef<LLMMessages.Command> llmActor;
    private final ActorRef<SafetyMessages.Command> safetyActor;
    private final ActorRef<LoggingMessages.Command> loggingActor;
    private final RAGService ragService;

    // Session state - tracks pending requests
    private final Map<String, PendingRequest> pendingRequests = new HashMap<>();

    // Timeout for actor responses
    private static final Duration ACTOR_TIMEOUT = Duration.ofSeconds(30);

    private SessionActor(
            ActorContext<Command> context,
            ActorRef<RAGMessages.Command> ragActor,
            ActorRef<LLMMessages.Command> llmActor,
            ActorRef<SafetyMessages.Command> safetyActor,
            ActorRef<LoggingMessages.Command> loggingActor,
            RAGService ragService) {
        super(context);
        this.ragActor = ragActor;
        this.llmActor = llmActor;
        this.safetyActor = safetyActor;
        this.loggingActor = loggingActor;
        this.ragService = ragService;
        log.info("SessionActor started at path: {}", context.getSelf().path());
    }

    public static Behavior<Command> create(
            ActorRef<RAGMessages.Command> ragActor,
            ActorRef<LLMMessages.Command> llmActor,
            ActorRef<SafetyMessages.Command> safetyActor,
            ActorRef<LoggingMessages.Command> loggingActor,
            RAGService ragService) {
        return Behaviors.setup(context -> new SessionActor(
                context, ragActor, llmActor, safetyActor, loggingActor, ragService));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(UserRequest.class, this::onUserRequest)
                .onMessage(RAGResultReceived.class, this::onRAGResult)
                .onMessage(LLMResultReceived.class, this::onLLMResult)
                .onMessage(SafetyResultReceived.class, this::onSafetyResult)
                .build();
    }

    /**
     * Step 1: Receive user request, start the pipeline by querying RAG
     */
    private Behavior<Command> onUserRequest(UserRequest request) {
        String sessionId = request.getSessionId();
        long startTime = System.currentTimeMillis();

        log.info("Session {} - Processing: {}", sessionId, request.getUserQuery());

        // TELL: Log the incoming request (fire-and-forget)
        loggingActor.tell(LoggingMessages.LogInfo.create(
                "SessionActor",
                String.format("New request [%s]: %s", sessionId, request.getUserQuery())
        ));

        // Store pending request state
        pendingRequests.put(sessionId, new PendingRequest(
                request.getUserQuery(),
                request.getReplyTo(),
                startTime,
                null, null, null
        ));

        // ASK: Query RAG for similar commands (with adapter for response)
        ActorRef<RAGMessages.SearchResponse> ragResponseAdapter =
                getContext().messageAdapter(RAGMessages.SearchResponse.class,
                        response -> new RAGResultReceived(response));

        ragActor.tell(RAGMessages.SearchRequest.create(
                sessionId,
                request.getUserQuery(),
                3,  // top 3 results
                ragResponseAdapter
        ));

        return this;
    }

    /**
     * Step 2: RAG results received, now query LLM with context
     */
    private Behavior<Command> onRAGResult(RAGResultReceived message) {
        RAGMessages.SearchResponse response = message.getResponse();
        String sessionId = response.getSessionId();

        PendingRequest pending = pendingRequests.get(sessionId);
        if (pending == null) {
            log.warn("No pending request for session: {}", sessionId);
            return this;
        }

        log.debug("Session {} - RAG returned {} results", sessionId,
                response.getResults().size());

        // Format RAG results as context for LLM
        String ragContext = formatRAGContext(response.getResults());

        // Update pending state
        pendingRequests.put(sessionId, pending.withRagContext(ragContext));

        // ASK: Query LLM with RAG context
        ActorRef<LLMMessages.GitQueryResponse> llmResponseAdapter =
                getContext().messageAdapter(LLMMessages.GitQueryResponse.class,
                        resp -> new LLMResultReceived(resp));

        llmActor.tell(LLMMessages.GitQueryRequest.create(
                sessionId,
                pending.userQuery(),
                ragContext,
                llmResponseAdapter
        ));

        return this;
    }

    /**
     * Step 3: LLM result received, FORWARD to SafetyActor for validation
     */
    private Behavior<Command> onLLMResult(LLMResultReceived message) {
        LLMMessages.GitQueryResponse response = message.getResponse();
        String sessionId = response.getSessionId();

        PendingRequest pending = pendingRequests.get(sessionId);
        if (pending == null) {
            log.warn("No pending request for session: {}", sessionId);
            return this;
        }

        log.debug("Session {} - LLM generated: {}", sessionId, response.getGitCommand());

        // Update pending state with LLM response
        pendingRequests.put(sessionId, pending.withLlmResponse(response));

        if (!response.isSuccess()) {
            // LLM failed, respond with error
            sendFinalResponse(sessionId, pending, response, null);
            return this;
        }

        // FORWARD: Send to SafetyActor for safety check
        // The SafetyActor will analyze the command
        ActorRef<SafetyMessages.SafetyCheckResponse> safetyResponseAdapter =
                getContext().messageAdapter(SafetyMessages.SafetyCheckResponse.class,
                        resp -> new SafetyResultReceived(resp));

        safetyActor.tell(SafetyMessages.SafetyCheckRequest.create(
                sessionId,
                response.getGitCommand(),
                pending.userQuery(),
                safetyResponseAdapter
        ));

        return this;
    }

    /**
     * Step 4: Safety result received, send final response to user
     */
    private Behavior<Command> onSafetyResult(SafetyResultReceived message) {
        SafetyMessages.SafetyCheckResponse response = message.getResponse();
        String sessionId = response.getSessionId();

        PendingRequest pending = pendingRequests.get(sessionId);
        if (pending == null) {
            log.warn("No pending request for session: {}", sessionId);
            return this;
        }

        log.debug("Session {} - Safety: {} (approved: {})",
                sessionId, response.getSafetyLevel(), response.isApproved());

        sendFinalResponse(sessionId, pending, pending.llmResponse(), response);

        return this;
    }

    /**
     * Send final response back to original requester
     */
    private void sendFinalResponse(
            String sessionId,
            PendingRequest pending,
            LLMMessages.GitQueryResponse llmResponse,
            SafetyMessages.SafetyCheckResponse safetyResponse) {

        long responseTime = System.currentTimeMillis() - pending.startTime();

        UserResponse response;
        if (llmResponse == null || !llmResponse.isSuccess()) {
            response = UserResponse.failure(
                    sessionId,
                    pending.userQuery(),
                    llmResponse != null ? llmResponse.getErrorMessage() : "Unknown error",
                    responseTime
            );
        } else {
            response = UserResponse.success(
                    sessionId,
                    pending.userQuery(),
                    llmResponse.getGitCommand(),
                    llmResponse.getExplanation(),
                    safetyResponse != null ? safetyResponse.getSafetyLevel() : llmResponse.getSafetyLevel(),
                    safetyResponse != null ? safetyResponse.getWarnings() : List.of(),
                    safetyResponse != null ? safetyResponse.getAlternatives() : List.of(),
                    responseTime
            );
        }

        // Send response to original requester
        pending.replyTo().tell(response);

        // TELL: Log the completed interaction
        loggingActor.tell(LoggingMessages.LogUserQuery.create(
                sessionId,
                pending.userQuery(),
                llmResponse != null ? llmResponse.getGitCommand() : null,
                safetyResponse != null ? safetyResponse.getSafetyLevel().name() : "UNKNOWN",
                responseTime
        ));

        // Clean up
        pendingRequests.remove(sessionId);

        log.info("Session {} - Completed in {}ms", sessionId, responseTime);
    }

    private String formatRAGContext(List<RAGMessages.GitCommandResult> results) {
        if (results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            RAGMessages.GitCommandResult r = results.get(i);
            sb.append(String.format("%d. %s - %s (Risk: %s)\n",
                    i + 1, r.getCommand(), r.getDescription(), r.getRiskLevel()));
        }
        return sb.toString();
    }

    // Helper record for tracking pending requests
    private record PendingRequest(
            String userQuery,
            ActorRef<UserResponse> replyTo,
            long startTime,
            String ragContext,
            LLMMessages.GitQueryResponse llmResponse,
            SafetyMessages.SafetyCheckResponse safetyResponse
    ) {
        PendingRequest withRagContext(String context) {
            return new PendingRequest(userQuery, replyTo, startTime, context, llmResponse, safetyResponse);
        }

        PendingRequest withLlmResponse(LLMMessages.GitQueryResponse response) {
            return new PendingRequest(userQuery, replyTo, startTime, ragContext, response, safetyResponse);
        }
    }
}