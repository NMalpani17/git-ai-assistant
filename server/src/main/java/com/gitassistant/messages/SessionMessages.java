package com.gitassistant.messages;

import akka.actor.typed.ActorRef;
import lombok.Value;
import java.util.List;

/**
 * Messages for SessionActor - main orchestrator
 */
public class SessionMessages {

    public interface Command extends CborSerializable {}

    // External request from REST controller
    @Value
    public static class UserRequest implements Command {
        String sessionId;
        String userQuery;
        ActorRef<UserResponse> replyTo;

        public static UserRequest create(String sessionId, String userQuery,
                                         ActorRef<UserResponse> replyTo) {
            return new UserRequest(sessionId, userQuery, replyTo);
        }
    }

    // Final response back to user
    @Value
    public static class UserResponse implements CborSerializable {
        String sessionId;
        String userQuery;
        String gitCommand;
        String explanation;
        LLMMessages.SafetyLevel safetyLevel;
        List<String> warnings;
        List<String> alternatives;
        long responseTimeMs;
        boolean success;
        String errorMessage;

        public static UserResponse success(String sessionId, String userQuery,
                                           String gitCommand, String explanation, LLMMessages.SafetyLevel safetyLevel,
                                           List<String> warnings, List<String> alternatives, long responseTimeMs) {
            return new UserResponse(sessionId, userQuery, gitCommand, explanation,
                    safetyLevel, warnings, alternatives, responseTimeMs, true, null);
        }

        public static UserResponse failure(String sessionId, String userQuery,
                                           String error, long responseTimeMs) {
            return new UserResponse(sessionId, userQuery, null, null,
                    LLMMessages.SafetyLevel.UNKNOWN, List.of(), List.of(),
                    responseTimeMs, false, error);
        }
    }

    // Internal messages for orchestration
    @Value
    public static class RAGResultReceived implements Command {
        RAGMessages.SearchResponse response;
    }

    @Value
    public static class LLMResultReceived implements Command {
        LLMMessages.GitQueryResponse response;
    }

    @Value
    public static class SafetyResultReceived implements Command {
        SafetyMessages.SafetyCheckResponse response;
    }
}