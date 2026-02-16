package com.gitassistant.controllers;

import akka.actor.typed.ActorSystem;
import akka.cluster.typed.Cluster;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import com.gitassistant.config.AkkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Controller for viewing Akka Cluster status.
 */
@RestController
@RequestMapping("/api/cluster")
@RequiredArgsConstructor
@Slf4j
public class ClusterController {

    private final ActorSystem<AkkaConfig.GuardianActor.Command> actorSystem;

    @Value("${akka.cluster.port:2551}")
    private int akkaPort;

    @Value("${server.port:8080}")
    private int httpPort;

    /**
     * Get cluster status and member information.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getClusterStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        try {
            Cluster cluster = Cluster.get(actorSystem);

            Member selfMember = cluster.selfMember();
            status.put("self", Map.of(
                    "address", selfMember.address().toString(),
                    "status", selfMember.status().toString(),
                    "roles", selfMember.getRoles(),
                    "akkaPort", akkaPort,
                    "httpPort", httpPort
            ));

            status.put("state", Map.of(
                    "leader", cluster.state().getLeader() != null ?
                            cluster.state().getLeader().toString() : "none",
                    "isLeader", cluster.selfMember().address().equals(cluster.state().getLeader())
            ));

            List<Map<String, Object>> members = new ArrayList<>();
            for (Member member : cluster.state().getMembers()) {
                Map<String, Object> memberInfo = new LinkedHashMap<>();
                memberInfo.put("address", member.address().toString());
                memberInfo.put("status", member.status().toString());
                memberInfo.put("roles", new ArrayList<>(member.getRoles()));
                memberInfo.put("isUp", member.status() == MemberStatus.up());
                memberInfo.put("isSelf", member.address().equals(selfMember.address()));
                members.add(memberInfo);
            }
            status.put("members", members);
            status.put("memberCount", members.size());

            Set<String> unreachable = StreamSupport
                    .stream(cluster.state().getUnreachable().spliterator(), false)
                    .map(m -> m.address().toString())
                    .collect(Collectors.toSet());
            status.put("unreachable", unreachable);

            status.put("success", true);

        } catch (Exception e) {
            log.error("Error getting cluster status: {}", e.getMessage(), e);
            status.put("success", false);
            status.put("error", e.getMessage());
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Get simple cluster health.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getClusterHealth() {
        Map<String, Object> health = new LinkedHashMap<>();

        try {
            Cluster cluster = Cluster.get(actorSystem);

            int upMembers = 0;
            int totalMembers = 0;

            for (Member member : cluster.state().getMembers()) {
                totalMembers++;
                if (member.status() == MemberStatus.up()) {
                    upMembers++;
                }
            }

            boolean isHealthy = upMembers > 0 && cluster.state().getUnreachable().isEmpty();

            health.put("status", isHealthy ? "HEALTHY" : "DEGRADED");
            health.put("upMembers", upMembers);
            health.put("totalMembers", totalMembers);
            health.put("unreachableCount", cluster.state().getUnreachable().size());
            health.put("selfStatus", cluster.selfMember().status().toString());
            health.put("isLeader", cluster.selfMember().address().equals(cluster.state().getLeader()));

        } catch (Exception e) {
            health.put("status", "ERROR");
            health.put("error", e.getMessage());
        }

        return ResponseEntity.ok(health);
    }

    /**
     * Get info about this specific node.
     */
    @GetMapping("/node")
    public ResponseEntity<Map<String, Object>> getNodeInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        try {
            Cluster cluster = Cluster.get(actorSystem);
            Member self = cluster.selfMember();

            info.put("nodeId", self.uniqueAddress().toString());
            info.put("address", self.address().toString());
            info.put("akkaPort", akkaPort);
            info.put("httpPort", httpPort);
            info.put("status", self.status().toString());
            info.put("roles", new ArrayList<>(self.getRoles()));
            info.put("isLeader", self.address().equals(cluster.state().getLeader()));
            info.put("upSince", self.upNumber());

            info.put("actors", List.of(
                    "LoggingActor (TELL pattern)",
                    "LLMActor (ASK pattern)",
                    "RAGActor (ASK pattern)",
                    "SafetyActor (FORWARD pattern)",
                    "SessionActor (Orchestrator)"
            ));

        } catch (Exception e) {
            info.put("error", e.getMessage());
        }

        return ResponseEntity.ok(info);
    }
}