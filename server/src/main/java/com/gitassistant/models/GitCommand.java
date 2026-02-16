package com.gitassistant.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a Git command stored with vector embedding.
 */
@Entity
@Table(name = "git_commands")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String command;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "usage_scenario", columnDefinition = "TEXT")
    private String usageScenario;

    @Column(columnDefinition = "TEXT")
    private String example;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column
    private String category;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}