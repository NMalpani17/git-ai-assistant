package com.gitassistant.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for generating text embeddings using Spring AI.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * Generate embedding for a single text.
     */
    public List<Double> generateEmbedding(String text) {
        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            // Spring AI returns List<Double>
            return response.getResult().getOutput();
        } catch (Exception e) {
            log.error("Failed to generate embedding for text: {}", text, e);
            throw new RuntimeException("Embedding generation failed", e);
        }
    }

    /**
     * Convert List<Double> to pgvector format string.
     * Example: [0.1, 0.2, 0.3] -> "[0.1,0.2,0.3]"
     */
    public String toPgVectorFormat(List<Double> embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            sb.append(embedding.get(i));
            if (i < embedding.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Generate embedding and return as pgvector format string.
     */
    public String generateEmbeddingAsVector(String text) {
        List<Double> embedding = generateEmbedding(text);
        return toPgVectorFormat(embedding);
    }
}