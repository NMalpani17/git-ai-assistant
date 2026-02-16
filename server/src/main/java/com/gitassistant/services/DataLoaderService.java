package com.gitassistant.services;

import com.gitassistant.models.GitCommand;
import com.gitassistant.repositories.GitCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to load Git commands dataset with embeddings into pgvector.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataLoaderService {

    private final GitCommandRepository gitCommandRepository;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void loadDataOnStartup() {
        if (gitCommandRepository.count() == 0) {
            log.info("Loading Git commands dataset...");
            loadGitCommands();
            log.info("Git commands dataset loaded successfully!");
        } else {
            log.info("Git commands already loaded. Count: {}", gitCommandRepository.count());
        }
    }

    private void loadGitCommands() {
        List<GitCommandData> commands = getGitCommandsDataset();

        for (GitCommandData data : commands) {
            try {
                // Save the command first
                GitCommand cmd = GitCommand.builder()
                        .command(data.command)
                        .description(data.description)
                        .usageScenario(data.usageScenario)
                        .example(data.example)
                        .riskLevel(data.riskLevel)
                        .category(data.category)
                        .build();

                GitCommand saved = gitCommandRepository.save(cmd);

                // Generate and store embedding
                String textForEmbedding = String.format("%s %s %s",
                        data.command, data.description, data.usageScenario);
                String embedding = embeddingService.generateEmbeddingAsVector(textForEmbedding);

                // Update with embedding using native query
                jdbcTemplate.update(
                        "UPDATE git_commands SET embedding = cast(? as vector) WHERE id = ?",
                        embedding, saved.getId()
                );

                log.debug("Loaded command: {}", data.command);

            } catch (Exception e) {
                log.error("Failed to load command {}: {}", data.command, e.getMessage());
            }
        }
    }

    private List<GitCommandData> getGitCommandsDataset() {
        return List.of(
                // Basic Commands
                new GitCommandData("git init", "Initialize a new Git repository",
                        "Starting a new project", "git init my-project", "SAFE", "basic"),
                new GitCommandData("git clone <url>", "Clone a repository from remote",
                        "Getting a copy of existing project", "git clone https://github.com/user/repo.git", "SAFE", "basic"),
                new GitCommandData("git add <file>", "Stage changes for commit",
                        "Preparing files to commit", "git add . or git add file.txt", "SAFE", "basic"),
                new GitCommandData("git commit -m '<message>'", "Commit staged changes",
                        "Saving your work with a message", "git commit -m 'Add new feature'", "SAFE", "basic"),
                new GitCommandData("git status", "Show working tree status",
                        "Check what files changed", "git status", "SAFE", "basic"),
                new GitCommandData("git log", "Show commit history",
                        "View past commits", "git log --oneline", "SAFE", "basic"),
                new GitCommandData("git diff", "Show changes between commits or working tree",
                        "See what changed in files", "git diff HEAD~1", "SAFE", "basic"),
                new GitCommandData("git pull", "Fetch and merge from remote",
                        "Get latest changes from team", "git pull origin main", "SAFE", "basic"),
                new GitCommandData("git push", "Push commits to remote repository",
                        "Share your commits with team", "git push origin main", "SAFE", "basic"),
                new GitCommandData("git fetch", "Download objects from remote",
                        "Get remote changes without merging", "git fetch origin", "SAFE", "basic"),

                // Branching
                new GitCommandData("git branch", "List, create, or delete branches",
                        "Manage branches", "git branch feature-login", "SAFE", "branching"),
                new GitCommandData("git branch <name>", "Create a new branch",
                        "Start working on a new feature", "git branch feature-auth", "SAFE", "branching"),
                new GitCommandData("git checkout <branch>", "Switch to a branch",
                        "Move to different branch", "git checkout develop", "SAFE", "branching"),
                new GitCommandData("git checkout -b <name>", "Create and switch to new branch",
                        "Start new feature branch quickly", "git checkout -b feature-api", "SAFE", "branching"),
                new GitCommandData("git switch <branch>", "Switch to a branch (newer syntax)",
                        "Modern way to change branches", "git switch main", "SAFE", "branching"),
                new GitCommandData("git merge <branch>", "Merge branch into current branch",
                        "Combine work from another branch", "git merge feature-login", "CAUTION", "branching"),
                new GitCommandData("git branch -d <name>", "Delete a merged branch",
                        "Clean up after merging", "git branch -d feature-done", "CAUTION", "branching"),
                new GitCommandData("git branch -D <name>", "Force delete a branch",
                        "Delete unmerged branch", "git branch -D abandoned-feature", "DANGEROUS", "branching"),

                // Undoing Changes
                new GitCommandData("git reset HEAD~1", "Undo last commit, keep changes staged",
                        "Undo commit but keep work", "git reset HEAD~1", "CAUTION", "undo"),
                new GitCommandData("git reset --soft HEAD~1", "Undo commit, keep changes staged",
                        "Recommit with different message", "git reset --soft HEAD~1", "CAUTION", "undo"),
                new GitCommandData("git reset --hard HEAD~1", "Undo commit and discard all changes",
                        "Completely remove last commit", "git reset --hard HEAD~1", "DANGEROUS", "undo"),
                new GitCommandData("git revert <commit>", "Create new commit that undoes changes",
                        "Safely undo a commit in shared history", "git revert abc123", "SAFE", "undo"),
                new GitCommandData("git checkout -- <file>", "Discard changes in working directory",
                        "Undo uncommitted changes to file", "git checkout -- file.txt", "CAUTION", "undo"),
                new GitCommandData("git restore <file>", "Restore file to last commit state",
                        "Discard local changes (newer syntax)", "git restore file.txt", "CAUTION", "undo"),
                new GitCommandData("git clean -fd", "Remove untracked files and directories",
                        "Clean up untracked files", "git clean -fd", "DANGEROUS", "undo"),

                // Stashing
                new GitCommandData("git stash", "Temporarily save uncommitted changes",
                        "Save work to switch branches", "git stash", "SAFE", "stash"),
                new GitCommandData("git stash pop", "Apply and remove latest stash",
                        "Restore stashed changes", "git stash pop", "SAFE", "stash"),
                new GitCommandData("git stash list", "List all stashes",
                        "See saved stashes", "git stash list", "SAFE", "stash"),
                new GitCommandData("git stash drop", "Delete a stash",
                        "Remove stash you don't need", "git stash drop stash@{0}", "CAUTION", "stash"),

                // Remote Operations
                new GitCommandData("git remote -v", "Show remote repositories",
                        "See connected remotes", "git remote -v", "SAFE", "remote"),
                new GitCommandData("git remote add <name> <url>", "Add a remote repository",
                        "Connect to new remote", "git remote add upstream https://github.com/original/repo.git", "SAFE", "remote"),
                new GitCommandData("git push --force", "Force push to remote",
                        "Overwrite remote history", "git push --force origin main", "DANGEROUS", "remote"),
                new GitCommandData("git push -f", "Force push (short form)",
                        "Overwrite remote history", "git push -f origin feature", "DANGEROUS", "remote"),
                new GitCommandData("git push --force-with-lease", "Safer force push",
                        "Force push only if no new commits", "git push --force-with-lease origin feature", "CAUTION", "remote"),

                // Rebasing
                new GitCommandData("git rebase <branch>", "Reapply commits on top of another branch",
                        "Keep linear history", "git rebase main", "CAUTION", "rebase"),
                new GitCommandData("git rebase -i HEAD~n", "Interactive rebase",
                        "Edit, squash, reorder commits", "git rebase -i HEAD~3", "CAUTION", "rebase"),
                new GitCommandData("git rebase --abort", "Cancel ongoing rebase",
                        "Stop rebase and restore state", "git rebase --abort", "SAFE", "rebase"),
                new GitCommandData("git rebase --continue", "Continue rebase after resolving conflicts",
                        "Proceed with rebase", "git rebase --continue", "SAFE", "rebase"),

                // Recovery
                new GitCommandData("git reflog", "Show reference log",
                        "Find lost commits", "git reflog", "SAFE", "recovery"),
                new GitCommandData("git cherry-pick <commit>", "Apply specific commit to current branch",
                        "Copy a commit from another branch", "git cherry-pick abc123", "SAFE", "recovery"),
                new GitCommandData("git bisect", "Binary search to find bug-introducing commit",
                        "Debug when bug was introduced", "git bisect start", "SAFE", "recovery"),

                // Tags
                new GitCommandData("git tag", "List tags",
                        "See all tags", "git tag", "SAFE", "tags"),
                new GitCommandData("git tag <name>", "Create a lightweight tag",
                        "Mark a release point", "git tag v1.0.0", "SAFE", "tags"),
                new GitCommandData("git tag -a <name> -m '<message>'", "Create annotated tag",
                        "Create tag with message", "git tag -a v1.0.0 -m 'Release 1.0'", "SAFE", "tags"),
                new GitCommandData("git push --tags", "Push all tags to remote",
                        "Share tags with team", "git push --tags", "SAFE", "tags")
        );
    }

    // Helper class for command data
    private record GitCommandData(
            String command,
            String description,
            String usageScenario,
            String example,
            String riskLevel,
            String category
    ) {}
}