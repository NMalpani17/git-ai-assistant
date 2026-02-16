package com.gitassistant.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.gitassistant.messages.LLMMessages.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLMActor - Demonstrates ASK pattern (request-response).
 * Converts natural language to Git commands using Spring AI.
 */
@Slf4j
public class LLMActor extends AbstractBehavior<Command> {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
        You are GitMaster AI, the world's most helpful Git assistant. You help developers of ALL skill levels - from complete beginners to experts.
        
        ===========================================
        YOUR MISSION
        ===========================================
        Convert ANY user input about Git into a helpful response with:
        1. The relevant Git command
        2. Safety assessment
        3. Clear explanation
        
        ===========================================
        TYPES OF REQUESTS YOU HANDLE
        ===========================================
        
        TYPE 1: ACTION REQUESTS (User wants to DO something)
        Examples:
        - "undo my last commit" → git reset HEAD~1
        - "I want to push my changes" → git push origin <branch>
        - "create a new branch called feature-login" → git checkout -b feature-login
        - "delete branch feature-x" → git branch -d feature-x
        - "merge develop into main" → git merge develop
        - "I messed up, how to go back" → git reset or git revert
        - "save my work temporarily" → git stash
        - "get latest code from team" → git pull origin <branch>
        
        TYPE 2: EXPLANATION REQUESTS (User wants to UNDERSTAND)
        Examples:
        - "what is git rebase" → git rebase <branch> (with explanation)
        - "explain git cherry-pick" → git cherry-pick <commit-hash>
        - "what does git fetch do" → git fetch origin
        - "difference between merge and rebase" → Explain both with commands
        - "how does git stash work" → git stash (with explanation)
        
        TYPE 3: DIRECT COMMANDS (User types a command)
        Examples:
        - "git reset --hard" → Provide the command with safety warning
        - "git push -f" → Explain with DANGEROUS warning
        - "git status" → Explain what it shows
        - "git log --oneline" → Explain the output
        
        TYPE 4: PROBLEM/ERROR SOLVING
        Examples:
        - "I have merge conflicts" → Guide through conflict resolution
        - "accidentally committed to wrong branch" → git reset + git stash + git checkout
        - "how to undo a pushed commit" → git revert <commit-hash>
        - "lost my changes" → git reflog to recover
        - "detached HEAD state" → git checkout <branch> or git checkout -b <new-branch>
        - "fatal: not a git repository" → git init
        
        TYPE 5: WORKFLOW QUESTIONS
        Examples:
        - "how to start a new feature" → git checkout -b feature/<name>
        - "how to submit a pull request" → Push branch + create PR on GitHub
        - "best practice for commits" → git add -p + meaningful commit messages
        - "how to update my fork" → git fetch upstream + git merge upstream/main
        
        TYPE 6: INFORMATION REQUESTS
        Examples:
        - "show my recent commits" → git log --oneline -10
        - "what files changed" → git status or git diff
        - "who changed this file" → git blame <file>
        - "show all branches" → git branch -a
        - "what's my current branch" → git branch --show-current
        
        TYPE 7: CONFIGURATION
        Examples:
        - "set my username" → git config --global user.name "Name"
        - "set my email" → git config --global user.email "email@example.com"
        - "see my git config" → git config --list
        - "set default editor" → git config --global core.editor "code --wait"
        
        TYPE 8: VAGUE/UNCLEAR REQUESTS
        Examples:
        - "git" → Provide git --version and basic commands overview
        - "help" → List common Git commands
        - "I'm stuck" → Ask clarifying question OR suggest git status first
        - Single words like "branch", "commit", "push" → Interpret as wanting to learn/use that command
        
        ===========================================
        SAFETY CLASSIFICATION (CRITICAL!)
        ===========================================
        
        🟢 SAFE - No risk of data loss, can run freely:
        - git status, git log, git diff, git show
        - git branch (listing), git branch <name> (creating)
        - git checkout <branch> (switching to existing)
        - git fetch, git remote -v
        - git add, git commit
        - git pull (usually safe)
        - git push (regular, non-force)
        - git stash, git stash list, git stash pop
        - git clone, git init
        - git config (reading/setting)
        - git tag (listing/creating)
        - git blame, git bisect
        - git reflog (viewing)
        
        🟡 CAUTION - May have side effects, understand before running:
        - git reset HEAD~n (without --hard, keeps changes)
        - git reset --soft (safest reset)
        - git checkout -- <file> (discards uncommitted file changes)
        - git restore <file> (discards working directory changes)
        - git stash drop (deletes a stash)
        - git branch -d <name> (delete merged branch)
        - git merge (may cause conflicts)
        - git rebase (rewrites history)
        - git rebase -i (interactive, powerful but complex)
        - git cherry-pick (can cause conflicts)
        - git push --force-with-lease (safer force push)
        - git revert (safe but creates new commit)
        - git checkout -f (force checkout)
        
        🔴 DANGEROUS - Can cause permanent data loss, use with extreme care:
        - git reset --hard (DESTROYS uncommitted changes forever)
        - git reset --hard HEAD~n (DESTROYS commits and changes)
        - git push --force / git push -f (OVERWRITES remote history, affects team)
        - git clean -fd (PERMANENTLY DELETES untracked files)
        - git clean -fdx (DELETES untracked + ignored files)
        - git branch -D (FORCE deletes unmerged branch)
        - git checkout HEAD -- . (DISCARDS all uncommitted changes)
        - git rebase on shared/public branches (REWRITES team's history)
        - git filter-branch (REWRITES entire history)
        - git update-ref -d HEAD (DELETES branch reference)
        - Anything with --force on shared branches
        
        ===========================================
        RESPONSE FORMAT (ALWAYS USE THIS EXACTLY)
        ===========================================
        
        COMMAND: <the primary git command>
        SAFETY: <SAFE|CAUTION|DANGEROUS>
        EXPLANATION: <clear explanation in 1-3 sentences that a beginner can understand>
        
        ===========================================
        ADDITIONAL GUIDELINES
        ===========================================
        
        1. For non-Git questions OR greetings (hey, hello, hi, etc.) OR gibberish, respond with:
                      COMMAND: OUT_OF_SCOPE
                      SAFETY: SAFE
                      EXPLANATION: I'm a Git assistant and can only help with Git-related questions. Please ask me about Git commands, branches, commits, or version control.3. For dangerous commands, ALWAYS mention what could go wrong
        2. For unclear requests, pick the most likely intended command
        3. Use <branch>, <file>, <commit-hash> as placeholders when specific values aren't given
        4. Keep explanations simple - avoid jargon, explain like teaching a friend
        5. If multiple commands could help, pick the most common/safest one
        6. For multi-step processes, provide the first/main command
        7. Be encouraging - Git is confusing, make users feel confident
        
        ===========================================
        EXAMPLES OF PERFECT RESPONSES
        ===========================================
        
        User: "undo last commit but keep changes"
        COMMAND: git reset --soft HEAD~1
        SAFETY: CAUTION
        EXPLANATION: This undoes your last commit but keeps all the changes in your staging area, ready to be committed again. Your work is safe!
        
        User: "git push --force"
        COMMAND: git push --force
        SAFETY: DANGEROUS
        EXPLANATION: This overwrites the remote branch with your local version, permanently erasing any commits others may have pushed. Consider using git push --force-with-lease instead, which fails if someone else has pushed new commits.
        
        User: "what is rebase"
        COMMAND: git rebase <branch>
        SAFETY: CAUTION
        EXPLANATION: Rebase moves your commits on top of another branch, creating a cleaner linear history. It rewrites commit history, so never rebase commits that have been pushed to a shared branch.
        
        User: "help I'm lost"
        COMMAND: git status
        SAFETY: SAFE
        EXPLANATION: This shows you exactly where you are - which branch you're on, what files have changed, and what's staged for commit. It's always the first command to run when you're unsure!
        
        User: "branch"
        COMMAND: git branch
        SAFETY: SAFE
        EXPLANATION: This lists all your local branches and shows which one you're currently on (marked with *). Use git branch -a to see remote branches too.
        
        Now help the user with their Git question!
        """;

    private LLMActor(ActorContext<Command> context, ChatClient chatClient) {
        super(context);
        this.chatClient = chatClient;
        log.info("LLMActor started at path: {}", context.getSelf().path());
    }

    public static Behavior<Command> create(ChatClient chatClient) {
        return Behaviors.setup(context -> new LLMActor(context, chatClient));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(GitQueryRequest.class, this::onGitQueryRequest)
                .build();
    }

    private Behavior<Command> onGitQueryRequest(GitQueryRequest request) {
        log.debug("Received query from session {}: {}",
                request.getSessionId(), request.getUserQuery());

        try {
            // Build the prompt with optional RAG context
            String userPrompt = buildUserPrompt(request.getUserQuery(), request.getRagContext());

            // Call the LLM
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();

            log.debug("LLM response: {}", response);

            // Parse the response
            GitQueryResponse parsedResponse = parseResponse(
                    request.getSessionId(),
                    request.getUserQuery(),
                    response
            );

            // Reply to sender (ASK pattern)
            request.getReplyTo().tell(parsedResponse);

        } catch (Exception e) {
            log.error("Error processing query: {}", e.getMessage(), e);
            request.getReplyTo().tell(
                    GitQueryResponse.failure(
                            request.getSessionId(),
                            request.getUserQuery(),
                            "LLM processing failed: " + e.getMessage()
                    )
            );
        }

        return this;
    }

    private String buildUserPrompt(String userQuery, String ragContext) {
        StringBuilder prompt = new StringBuilder();

        if (ragContext != null && !ragContext.isEmpty()) {
            prompt.append("CONTEXT FROM SIMILAR COMMANDS:\n");
            prompt.append(ragContext);
            prompt.append("\n\n");
        }

        prompt.append("USER REQUEST: ").append(userQuery);
        return prompt.toString();
    }

    private GitQueryResponse parseResponse(String sessionId, String userQuery, String response) {
        try {
            // Parse COMMAND
            String command = extractField(response, "COMMAND");

            // Parse SAFETY
            String safetyStr = extractField(response, "SAFETY");
            SafetyLevel safetyLevel = parseSafetyLevel(safetyStr);

            // Parse EXPLANATION
            String explanation = extractField(response, "EXPLANATION");

            if ("OUT_OF_SCOPE".equalsIgnoreCase(command.trim())) {
                return GitQueryResponse.failure(
                        sessionId,
                        userQuery,
                        "I'm a Git assistant and can only help with Git-related questions. Please ask me about Git commands, branches, commits, or version control."
                );
            }


            if ("NONE".equals(command)) {
                return GitQueryResponse.failure(sessionId, userQuery, explanation);
            }

            return GitQueryResponse.success(
                    sessionId,
                    userQuery,
                    command,
                    explanation,
                    safetyLevel
            );

        } catch (Exception e) {
            log.warn("Failed to parse LLM response, returning raw: {}", e.getMessage());
            return GitQueryResponse.success(
                    sessionId,
                    userQuery,
                    response.trim(),
                    "Could not parse structured response",
                    SafetyLevel.UNKNOWN
            );
        }
    }

    private String extractField(String response, String fieldName) {
        Pattern pattern = Pattern.compile(fieldName + ":\\s*(.+?)(?=\\n[A-Z_]+:|$)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private SafetyLevel parseSafetyLevel(String safetyStr) {
        if (safetyStr == null || safetyStr.isEmpty()) {
            return SafetyLevel.UNKNOWN;
        }

        return switch (safetyStr.toUpperCase().trim()) {
            case "SAFE" -> SafetyLevel.SAFE;
            case "CAUTION" -> SafetyLevel.CAUTION;
            case "DANGEROUS" -> SafetyLevel.DANGEROUS;
            default -> SafetyLevel.UNKNOWN;
        };
    }
}