package com.gitassistant.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.gitassistant.messages.LLMMessages.SafetyLevel;
import com.gitassistant.messages.SafetyMessages.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SafetyActor - Demonstrates FORWARD pattern.
 * Analyzes Git commands for potential risks and suggests alternatives.
 *
 * FORWARD pattern: SessionActor forwards messages to SafetyActor,
 * preserving the original sender so SafetyActor can reply directly.
 */
@Slf4j
public class SafetyActor extends AbstractBehavior<Command> {

    // Dangerous command patterns
    private static final List<DangerPattern> DANGER_PATTERNS = List.of(
            new DangerPattern(
                    Pattern.compile("git\\s+reset\\s+--hard", Pattern.CASE_INSENSITIVE),
                    "Permanently discards all uncommitted changes and commits",
                    List.of("git reset --soft HEAD~1 (keeps changes staged)",
                            "git stash (temporarily save changes)",
                            "git revert <commit> (safe undo)")
            ),
            new DangerPattern(
                    Pattern.compile("git\\s+push\\s+(-f|--force)(?!-with-lease)", Pattern.CASE_INSENSITIVE),
                    "Overwrites remote history, can cause data loss for team members",
                    List.of("git push --force-with-lease (safer, checks for new commits)",
                            "git revert <commit> then push (preserves history)")
            ),
            new DangerPattern(
                    Pattern.compile("git\\s+clean\\s+-f", Pattern.CASE_INSENSITIVE),
                    "Permanently deletes untracked files",
                    List.of("git clean -n (dry run first)",
                            "git stash --include-untracked (save instead of delete)")
            ),
            new DangerPattern(
                    Pattern.compile("git\\s+branch\\s+-D", Pattern.CASE_INSENSITIVE),
                    "Force deletes branch even if not merged",
                    List.of("git branch -d (only deletes if merged)",
                            "Check merge status first: git branch --merged")
            ),
            new DangerPattern(
                    Pattern.compile("git\\s+rebase.*main|git\\s+rebase.*master", Pattern.CASE_INSENSITIVE),
                    "Rebasing shared branches can cause conflicts for team",
                    List.of("git merge main (preserves history)",
                            "Only rebase local/feature branches")
            )
    );

    // Caution command patterns
    private static final List<CautionPattern> CAUTION_PATTERNS = List.of(
            new CautionPattern(
                    Pattern.compile("git\\s+reset(?!\\s+--hard)", Pattern.CASE_INSENSITIVE),
                    "Modifies commit history, use with care"
            ),
            new CautionPattern(
                    Pattern.compile("git\\s+checkout\\s+--", Pattern.CASE_INSENSITIVE),
                    "Discards local changes to file"
            ),
            new CautionPattern(
                    Pattern.compile("git\\s+restore(?!.*--staged)", Pattern.CASE_INSENSITIVE),
                    "Discards uncommitted changes"
            ),
            new CautionPattern(
                    Pattern.compile("git\\s+stash\\s+drop", Pattern.CASE_INSENSITIVE),
                    "Permanently deletes stashed changes"
            ),
            new CautionPattern(
                    Pattern.compile("git\\s+merge", Pattern.CASE_INSENSITIVE),
                    "May cause merge conflicts"
            ),
            new CautionPattern(
                    Pattern.compile("git\\s+rebase", Pattern.CASE_INSENSITIVE),
                    "Rewrites commit history"
            ),
            new CautionPattern(
                    Pattern.compile("git\\s+push\\s+--force-with-lease", Pattern.CASE_INSENSITIVE),
                    "Safer force push, but still overwrites remote"
            )
    );

    private SafetyActor(ActorContext<Command> context) {
        super(context);
        log.info("SafetyActor started at path: {}", context.getSelf().path());
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(SafetyActor::new);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(SafetyCheckRequest.class, this::onSafetyCheck)
                .build();
    }

    /**
     * Handles safety check requests.
     * This demonstrates the FORWARD pattern - the original sender is preserved
     * so we can reply directly to them.
     */
    private Behavior<Command> onSafetyCheck(SafetyCheckRequest request) {
        log.debug("Safety check for command: {}", request.getGitCommand());

        String command = request.getGitCommand();
        List<String> warnings = new ArrayList<>();
        List<String> alternatives = new ArrayList<>();
        SafetyLevel level = SafetyLevel.SAFE;

        // Check for dangerous patterns
        for (DangerPattern pattern : DANGER_PATTERNS) {
            if (pattern.pattern.matcher(command).find()) {
                level = SafetyLevel.DANGEROUS;
                warnings.add(pattern.warning);
                alternatives.addAll(pattern.alternatives);
                log.warn("DANGEROUS command detected: {} - {}", command, pattern.warning);
            }
        }

        // If not dangerous, check for caution patterns
        if (level == SafetyLevel.SAFE) {
            for (CautionPattern pattern : CAUTION_PATTERNS) {
                if (pattern.pattern.matcher(command).find()) {
                    level = SafetyLevel.CAUTION;
                    warnings.add(pattern.warning);
                    log.info("CAUTION command detected: {} - {}", command, pattern.warning);
                }
            }
        }

        // Build and send response (FORWARD pattern - reply goes to original sender)
        SafetyCheckResponse response;
        if (level == SafetyLevel.DANGEROUS) {
            response = SafetyCheckResponse.dangerous(
                    request.getSessionId(),
                    command,
                    warnings,
                    alternatives
            );
        } else if (level == SafetyLevel.CAUTION) {
            response = SafetyCheckResponse.caution(
                    request.getSessionId(),
                    command,
                    warnings,
                    alternatives
            );
        } else {
            response = SafetyCheckResponse.safe(
                    request.getSessionId(),
                    command
            );
        }

        // Reply to the original sender (preserved through FORWARD)
        request.getReplyTo().tell(response);

        return this;
    }

    // Helper record for dangerous patterns
    private record DangerPattern(Pattern pattern, String warning, List<String> alternatives) {}

    // Helper record for caution patterns
    private record CautionPattern(Pattern pattern, String warning) {}
}