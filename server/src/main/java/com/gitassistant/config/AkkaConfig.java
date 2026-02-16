package com.gitassistant.config;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.Behaviors;
import com.gitassistant.actors.*;
import com.gitassistant.messages.*;
import com.gitassistant.services.RAGService;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Spring configuration for Akka Actor System with Supervisor Strategies.
 * All 5 actors: LoggingActor, LLMActor, RAGActor, SafetyActor, SessionActor
 *
 * Supervisor Strategies:
 * - Resume: Actor continues processing (for transient errors)
 * - Restart: Actor is restarted (for recoverable errors)
 * - Stop: Actor is stopped (for fatal errors)
 * - Backoff: Exponential backoff before restart (for external service failures)
 */
@Configuration
@Slf4j
public class AkkaConfig {

    private ActorSystem<GuardianActor.Command> actorSystem;

    @Value("${akka.cluster.port:2551}")
    private int akkaPort;

    public static class GuardianActor {

        public interface Command {}

        public static class GetLoggingActor implements Command {
            public final ActorRef<ActorRef<LoggingMessages.Command>> replyTo;
            public GetLoggingActor(ActorRef<ActorRef<LoggingMessages.Command>> replyTo) { this.replyTo = replyTo; }
        }

        public static class GetLLMActor implements Command {
            public final ActorRef<ActorRef<LLMMessages.Command>> replyTo;
            public GetLLMActor(ActorRef<ActorRef<LLMMessages.Command>> replyTo) { this.replyTo = replyTo; }
        }

        public static class GetRAGActor implements Command {
            public final ActorRef<ActorRef<RAGMessages.Command>> replyTo;
            public GetRAGActor(ActorRef<ActorRef<RAGMessages.Command>> replyTo) { this.replyTo = replyTo; }
        }

        public static class GetSafetyActor implements Command {
            public final ActorRef<ActorRef<SafetyMessages.Command>> replyTo;
            public GetSafetyActor(ActorRef<ActorRef<SafetyMessages.Command>> replyTo) { this.replyTo = replyTo; }
        }

        public static class GetSessionActor implements Command {
            public final ActorRef<ActorRef<SessionMessages.Command>> replyTo;
            public GetSessionActor(ActorRef<ActorRef<SessionMessages.Command>> replyTo) { this.replyTo = replyTo; }
        }

        public static Behavior<Command> create(ChatClient chatClient, RAGService ragService) {
            return Behaviors.setup(context -> {
                context.getLog().info("Guardian actor starting, setting up supervised child actors...");

                // ============================================================
                // SUPERVISOR STRATEGY: RESUME
                // Use for: LoggingActor - logging errors shouldn't crash the actor
                // ============================================================
                Behavior<LoggingMessages.Command> loggingBehavior = Behaviors.supervise(
                        LoggingActor.create()
                ).onFailure(Exception.class, SupervisorStrategy.resume());

                ActorRef<LoggingMessages.Command> loggingActor =
                        context.spawn(loggingBehavior, "loggingActor");
                context.getLog().info("Guardian spawned LoggingActor with RESUME strategy");

                // ============================================================
                // SUPERVISOR STRATEGY: RESTART WITH BACKOFF
                // Use for: LLMActor - external API calls may fail temporarily
                // ============================================================
                Behavior<LLMMessages.Command> llmBehavior = Behaviors.supervise(
                        LLMActor.create(chatClient)
                ).onFailure(
                        Exception.class,
                        SupervisorStrategy.restartWithBackoff(
                                java.time.Duration.ofSeconds(1),
                                java.time.Duration.ofSeconds(30),
                                0.2
                        )
                );

                ActorRef<LLMMessages.Command> llmActor =
                        context.spawn(llmBehavior, "llmActor");
                context.getLog().info("Guardian spawned LLMActor with RESTART_BACKOFF strategy");

                // ============================================================
                // SUPERVISOR STRATEGY: RESTART WITH BACKOFF
                // Use for: RAGActor - database calls may fail temporarily
                // ============================================================
                Behavior<RAGMessages.Command> ragBehavior = Behaviors.supervise(
                        RAGActor.create(ragService)
                ).onFailure(
                        Exception.class,
                        SupervisorStrategy.restartWithBackoff(
                                java.time.Duration.ofMillis(500),
                                java.time.Duration.ofSeconds(10),
                                0.1
                        )
                );

                ActorRef<RAGMessages.Command> ragActor =
                        context.spawn(ragBehavior, "ragActor");
                context.getLog().info("Guardian spawned RAGActor with RESTART_BACKOFF strategy");

                // ============================================================
                // SUPERVISOR STRATEGY: RESTART
                // Use for: SafetyActor - stateless, safe to restart immediately
                // ============================================================
                Behavior<SafetyMessages.Command> safetyBehavior = Behaviors.supervise(
                        SafetyActor.create()
                ).onFailure(Exception.class, SupervisorStrategy.restart());

                ActorRef<SafetyMessages.Command> safetyActor =
                        context.spawn(safetyBehavior, "safetyActor");
                context.getLog().info("Guardian spawned SafetyActor with RESTART strategy");

                // ============================================================
                // SUPERVISOR STRATEGY: RESTART
                // Use for: SessionActor - orchestrator, stateful but can recover
                // ============================================================
                Behavior<SessionMessages.Command> sessionBehavior = Behaviors.supervise(
                        SessionActor.create(ragActor, llmActor, safetyActor, loggingActor, ragService)
                ).onFailure(Exception.class, SupervisorStrategy.restart());

                ActorRef<SessionMessages.Command> sessionActor =
                        context.spawn(sessionBehavior, "sessionActor");
                context.getLog().info("Guardian spawned SessionActor with RESTART strategy");

                loggingActor.tell(LoggingMessages.LogInfo.create(
                        "GuardianActor",
                        "All 5 actors initialized with supervision strategies: " +
                                "Logging(RESUME), LLM(BACKOFF), RAG(BACKOFF), Safety(RESTART), Session(RESTART)"
                ));

                return Behaviors.receive(Command.class)
                        .onMessage(GetLoggingActor.class, msg -> { msg.replyTo.tell(loggingActor); return Behaviors.same(); })
                        .onMessage(GetLLMActor.class, msg -> { msg.replyTo.tell(llmActor); return Behaviors.same(); })
                        .onMessage(GetRAGActor.class, msg -> { msg.replyTo.tell(ragActor); return Behaviors.same(); })
                        .onMessage(GetSafetyActor.class, msg -> { msg.replyTo.tell(safetyActor); return Behaviors.same(); })
                        .onMessage(GetSessionActor.class, msg -> { msg.replyTo.tell(sessionActor); return Behaviors.same(); })
                        .build();
            });
        }
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public ActorSystem<GuardianActor.Command> actorSystem(
            ChatClient chatClient, RAGService ragService) {

        Config baseConfig = ConfigFactory.load("akka.conf");

        Config portOverride = ConfigFactory.empty()
                .withValue("akka.remote.artery.canonical.port",
                        ConfigValueFactory.fromAnyRef(akkaPort));

        Config finalConfig = portOverride.withFallback(baseConfig);

        log.info("Starting Akka ActorSystem on port: {}", akkaPort);

        actorSystem = ActorSystem.create(
                GuardianActor.create(chatClient, ragService),
                "GitAssistantSystem",
                finalConfig
        );

        log.info("Akka ActorSystem 'GitAssistantSystem' created on port {} with Guardian and Supervisor Strategies", akkaPort);
        return actorSystem;
    }

    @Bean
    public ActorRef<LoggingMessages.Command> loggingActor(ActorSystem<GuardianActor.Command> system) {
        return getActorRef(system, GuardianActor.GetLoggingActor::new, "LoggingActor");
    }

    @Bean
    public ActorRef<LLMMessages.Command> llmActor(ActorSystem<GuardianActor.Command> system) {
        return getActorRef(system, GuardianActor.GetLLMActor::new, "LLMActor");
    }

    @Bean
    public ActorRef<RAGMessages.Command> ragActor(ActorSystem<GuardianActor.Command> system) {
        return getActorRef(system, GuardianActor.GetRAGActor::new, "RAGActor");
    }

    @Bean
    public ActorRef<SafetyMessages.Command> safetyActor(ActorSystem<GuardianActor.Command> system) {
        return getActorRef(system, GuardianActor.GetSafetyActor::new, "SafetyActor");
    }

    @Bean
    public ActorRef<SessionMessages.Command> sessionActor(ActorSystem<GuardianActor.Command> system) {
        return getActorRef(system, GuardianActor.GetSessionActor::new, "SessionActor");
    }

    private <T> ActorRef<T> getActorRef(
            ActorSystem<GuardianActor.Command> system,
            java.util.function.Function<ActorRef<ActorRef<T>>, GuardianActor.Command> messageFactory,
            String actorName) {

        java.time.Duration timeout = java.time.Duration.ofSeconds(3);

        try {
            CompletionStage<ActorRef<T>> result =
                    akka.actor.typed.javadsl.AskPattern.ask(
                            system, replyTo -> messageFactory.apply(replyTo), timeout, system.scheduler());

            ActorRef<T> actorRef = result.toCompletableFuture().get(3, TimeUnit.SECONDS);
            log.info("{} bean created: {}", actorName, actorRef.path());
            return actorRef;

        } catch (Exception e) {
            throw new RuntimeException("Failed to get " + actorName + " from Guardian", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (actorSystem != null) {
            log.info("Shutting down Akka ActorSystem...");
            actorSystem.terminate();
        }
    }
}