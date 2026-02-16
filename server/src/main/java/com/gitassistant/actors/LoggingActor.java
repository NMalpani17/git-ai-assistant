package com.gitassistant.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.gitassistant.messages.LoggingMessages.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * LoggingActor - Demonstrates TELL pattern (fire-and-forget).
 * Receives log messages and stores them. No response is sent back.
 */
@Slf4j
public class LoggingActor extends AbstractBehavior<Command> {

    // In-memory log storage (in production, persist to DB)
    private final List<LogInfo> infoLogs = new ArrayList<>();
    private final List<LogWarning> warningLogs = new ArrayList<>();
    private final List<LogError> errorLogs = new ArrayList<>();
    private final List<LogUserQuery> queryLogs = new ArrayList<>();

    private LoggingActor(ActorContext<Command> context) {
        super(context);
        log.info("LoggingActor started at path: {}", context.getSelf().path());
    }

    // Factory method to create the actor
    public static Behavior<Command> create() {
        return Behaviors.setup(LoggingActor::new);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(LogInfo.class, this::onLogInfo)
                .onMessage(LogWarning.class, this::onLogWarning)
                .onMessage(LogError.class, this::onLogError)
                .onMessage(LogUserQuery.class, this::onLogUserQuery)
                .build();
    }

    private Behavior<Command> onLogInfo(LogInfo msg) {
        log.info("[{}] {}", msg.getSource(), msg.getMessage());
        infoLogs.add(msg);
        return this;
    }

    private Behavior<Command> onLogWarning(LogWarning msg) {
        log.warn("[{}] {}", msg.getSource(), msg.getMessage());
        warningLogs.add(msg);
        return this;
    }

    private Behavior<Command> onLogError(LogError msg) {
        log.error("[{}] {} - Details: {}", msg.getSource(),
                msg.getMessage(), msg.getErrorDetails());
        errorLogs.add(msg);
        return this;
    }

    private Behavior<Command> onLogUserQuery(LogUserQuery msg) {
        log.info("[Session: {}] Query: '{}' -> Command: '{}' | Safety: {} | Time: {}ms",
                msg.getSessionId(),
                msg.getUserQuery(),
                msg.getGeneratedCommand(),
                msg.getSafetyLevel(),
                msg.getResponseTimeMs());
        queryLogs.add(msg);
        return this;
    }

    // Getters for testing/monitoring
    public int getInfoLogCount() { return infoLogs.size(); }
    public int getWarningLogCount() { return warningLogs.size(); }
    public int getErrorLogCount() { return errorLogs.size(); }
    public int getQueryLogCount() { return queryLogs.size(); }
}