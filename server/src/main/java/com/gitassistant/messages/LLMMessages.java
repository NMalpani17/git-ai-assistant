package com.gitassistant.messages;

import akka.actor.typed.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Messages for LLMActor - demonstrates ASK pattern (request-response)
 */
public class LLMMessages {

    public interface Command extends CborSerializable {}

    // Request: Natural language query to convert to Git command
    @Getter
    @AllArgsConstructor
    public static class GitQueryRequest implements Command {
        private final String sessionId;
        private final String userQuery;
        private final String ragContext;  // Context from RAG search
        private final ActorRef<GitQueryResponse> replyTo;

        public static GitQueryRequest create(String sessionId, String userQuery,
                                             String ragContext, ActorRef<GitQueryResponse> replyTo) {
            return new GitQueryRequest(sessionId, userQuery, ragContext, replyTo);
        }
    }

    // Response: Generated Git command with explanation
    @Getter
    @AllArgsConstructor
    public static class GitQueryResponse implements CborSerializable {
        private final String sessionId;
        private final String userQuery;
        private final String gitCommand;
        private final String explanation;
        private final SafetyLevel safetyLevel;
        private final boolean success;
        private final String errorMessage;

        public static GitQueryResponse success(String sessionId, String userQuery,
                                               String gitCommand, String explanation, SafetyLevel safetyLevel) {
            return new GitQueryResponse(sessionId, userQuery, gitCommand,
                    explanation, safetyLevel, true, null);
        }

        public static GitQueryResponse failure(String sessionId, String userQuery, String error) {
            return new GitQueryResponse(sessionId, userQuery, null, null,
                    SafetyLevel.UNKNOWN, false, error);
        }
    }

    public enum SafetyLevel {
        SAFE,       // No risk
        CAUTION,    // Needs confirmation
        DANGEROUS,  // Can cause data loss
        UNKNOWN     // Could not determine
    }
}