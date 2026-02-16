package com.gitassistant.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.gitassistant.messages.RAGMessages.*;
import com.gitassistant.models.GitCommand;
import com.gitassistant.services.RAGService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAGActor - Retrieval Augmented Generation using pgvector.
 * Demonstrates ASK pattern with database integration.
 */
@Slf4j
public class RAGActor extends AbstractBehavior<Command> {

    private final RAGService ragService;

    private RAGActor(ActorContext<Command> context, RAGService ragService) {
        super(context);
        this.ragService = ragService;
        log.info("RAGActor started at path: {}", context.getSelf().path());
    }

    public static Behavior<Command> create(RAGService ragService) {
        return Behaviors.setup(context -> new RAGActor(context, ragService));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(SearchRequest.class, this::onSearchRequest)
                .build();
    }

    private Behavior<Command> onSearchRequest(SearchRequest request) {
        log.debug("RAG search request from session {}: {}",
                request.getSessionId(), request.getQuery());

        try {
            // Search for similar commands
            List<GitCommand> results = ragService.searchSimilarCommands(
                    request.getQuery(),
                    request.getTopK()
            );

            // Convert to response format
            List<GitCommandResult> commandResults = results.stream()
                    .map(cmd -> new GitCommandResult(
                            cmd.getCommand(),
                            cmd.getDescription(),
                            cmd.getUsageScenario(),
                            cmd.getExample(),
                            cmd.getRiskLevel(),
                            0.0  // Similarity score (pgvector doesn't return it directly)
                    ))
                    .collect(Collectors.toList());

            // Reply with results (ASK pattern)
            request.getReplyTo().tell(SearchResponse.success(
                    request.getSessionId(),
                    commandResults
            ));

        } catch (Exception e) {
            log.error("RAG search failed: {}", e.getMessage(), e);
            request.getReplyTo().tell(SearchResponse.failure(
                    request.getSessionId(),
                    "RAG search failed: " + e.getMessage()
            ));
        }

        return this;
    }
}