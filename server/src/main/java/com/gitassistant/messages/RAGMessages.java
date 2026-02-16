package com.gitassistant.messages;

import akka.actor.typed.ActorRef;
import lombok.Value;
import java.util.List;

/**
 * Messages for RAGActor - vector similarity search
 */
public class RAGMessages {

    public interface Command extends CborSerializable {}

    // Request: Search for similar Git commands
    @Value
    public static class SearchRequest implements Command {
        String sessionId;
        String query;
        int topK;
        ActorRef<SearchResponse> replyTo;

        public static SearchRequest create(String sessionId, String query,
                                           int topK, ActorRef<SearchResponse> replyTo) {
            return new SearchRequest(sessionId, query, topK, replyTo);
        }
    }

    // Response: List of relevant Git commands
    @Value
    public static class SearchResponse implements CborSerializable {
        String sessionId;
        List<GitCommandResult> results;
        boolean success;
        String errorMessage;

        public static SearchResponse success(String sessionId, List<GitCommandResult> results) {
            return new SearchResponse(sessionId, results, true, null);
        }

        public static SearchResponse failure(String sessionId, String error) {
            return new SearchResponse(sessionId, List.of(), false, error);
        }
    }

    // Individual search result
    @Value
    public static class GitCommandResult implements CborSerializable {
        String command;
        String description;
        String usageScenario;
        String example;
        String riskLevel;
        double similarityScore;
    }
}