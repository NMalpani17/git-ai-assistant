package com.gitassistant.messages;

import akka.actor.typed.ActorRef;
import lombok.Value;
import java.util.List;

/**
 * Messages for SafetyActor - demonstrates FORWARD pattern
 */
public class SafetyMessages {

    public interface Command extends CborSerializable {}

    // Request: Check if a Git command is safe
    @Value
    public static class SafetyCheckRequest implements Command {
        String sessionId;
        String gitCommand;
        String userQuery;
        ActorRef<SafetyCheckResponse> replyTo;

        public static SafetyCheckRequest create(String sessionId, String gitCommand,
                                                String userQuery, ActorRef<SafetyCheckResponse> replyTo) {
            return new SafetyCheckRequest(sessionId, gitCommand, userQuery, replyTo);
        }
    }

    // Response: Safety analysis result
    @Value
    public static class SafetyCheckResponse implements CborSerializable {
        String sessionId;
        String gitCommand;
        LLMMessages.SafetyLevel safetyLevel;
        List<String> warnings;
        List<String> alternatives;
        boolean approved;

        public static SafetyCheckResponse safe(String sessionId, String gitCommand) {
            return new SafetyCheckResponse(sessionId, gitCommand,
                    LLMMessages.SafetyLevel.SAFE, List.of(), List.of(), true);
        }

        public static SafetyCheckResponse caution(String sessionId, String gitCommand,
                                                  List<String> warnings, List<String> alternatives) {
            return new SafetyCheckResponse(sessionId, gitCommand,
                    LLMMessages.SafetyLevel.CAUTION, warnings, alternatives, true);
        }

        public static SafetyCheckResponse dangerous(String sessionId, String gitCommand,
                                                    List<String> warnings, List<String> alternatives) {
            return new SafetyCheckResponse(sessionId, gitCommand,
                    LLMMessages.SafetyLevel.DANGEROUS, warnings, alternatives, false);
        }
    }
}