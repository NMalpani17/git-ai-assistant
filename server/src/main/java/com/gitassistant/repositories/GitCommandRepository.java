package com.gitassistant.repositories;

import com.gitassistant.models.GitCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for GitCommand with vector similarity search.
 */
@Repository
public interface GitCommandRepository extends JpaRepository<GitCommand, Long> {

    /**
     * Find similar commands using pgvector cosine similarity.
     * The embedding parameter should be passed as a formatted vector string.
     */
    @Query(value = """
            SELECT id, command, description, usage_scenario, example, risk_level, category, created_at
            FROM git_commands
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> cast(:embedding as vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<GitCommand> findSimilarCommands(
            @Param("embedding") String embedding,
            @Param("limit") int limit
    );

    /**
     * Find commands by category.
     */
    List<GitCommand> findByCategory(String category);

    /**
     * Find commands by risk level.
     */
    List<GitCommand> findByRiskLevel(String riskLevel);
}