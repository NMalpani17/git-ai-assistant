package com.gitassistant.services;

import com.gitassistant.models.GitCommand;
import com.gitassistant.repositories.GitCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for RAG (Retrieval Augmented Generation) operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RAGService {

    private final GitCommandRepository gitCommandRepository;
    private final EmbeddingService embeddingService;

    /**
     * Search for similar Git commands based on user query.
     */
    public List<GitCommand> searchSimilarCommands(String query, int topK) {
        try {
            // Generate embedding for the query
            String embeddingVector = embeddingService.generateEmbeddingAsVector(query);

            // Search using pgvector cosine similarity
            List<GitCommand> results = gitCommandRepository.findSimilarCommands(embeddingVector, topK);

            log.debug("Found {} similar commands for query: {}", results.size(), query);
            return results;

        } catch (Exception e) {
            log.error("Error searching for similar commands: {}", e.getMessage(), e);
            return List.of();
        }
    }
}