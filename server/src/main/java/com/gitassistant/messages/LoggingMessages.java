package com.gitassistant.messages;

import lombok.Value;
import java.time.Instant;

/**
 * Messages for LoggingActor - demonstrates TELL pattern (fire-and-forget)
 */
public class LoggingMessages {

    // Base interface for all logging commands
    public interface Command extends CborSerializable {}

    @Value
    public static class LogInfo implements Command {
        String source;
        String message;
        Instant timestamp;

        public static LogInfo create(String source, String message) {
            return new LogInfo(source, message, Instant.now());
        }
    }

    @Value
    public static class LogWarning implements Command {
        String source;
        String message;
        Instant timestamp;

        public static LogWarning create(String source, String message) {
            return new LogWarning(source, message, Instant.now());
        }
    }

    @Value
    public static class LogError implements Command {
        String source;
        String message;
        String errorDetails;
        Instant timestamp;

        public static LogError create(String source, String message, String errorDetails) {
            return new LogError(source, message, errorDetails, Instant.now());
        }
    }

    @Value
    public static class LogUserQuery implements Command {
        String sessionId;
        String userQuery;
        String generatedCommand;
        String safetyLevel;
        long responseTimeMs;
        Instant timestamp;

        public static LogUserQuery create(String sessionId, String userQuery,
                                          String generatedCommand, String safetyLevel, long responseTimeMs) {
            return new LogUserQuery(sessionId, userQuery, generatedCommand,
                    safetyLevel, responseTimeMs, Instant.now());
        }
    }
}