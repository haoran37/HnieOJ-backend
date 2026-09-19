package com.hnieacm.judge.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.judge.NodeProtocolConstants;
import com.hnieacm.judge.dto.NodeAuthChallengeVo;
import com.hnieacm.judge.dto.NodeAuthResult;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.properties.NodeSecurityProperties;
import com.hnieacm.judge.service.JudgeNodeLifecycleService;
import com.hnieacm.judge.service.NodeIdentityService;
import com.hnieacm.submission.service.JudgeResultReportService;
import com.hnieacm.submission.service.JudgeTaskClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WSS 连接级状态回归：requestId 防冲突窗口、READY credit 消耗、排空语义、进度/终态帧区分、容量原子性。
 *
 * @author Codex
 */
class JudgeNodeWebSocketHandlerStateTest {

    private JudgeNodeWebSocketHandler.NodeConnection connection() {
        JudgeNodeWebSocketHandler.NodeConnection connection =
                new JudgeNodeWebSocketHandler.NodeConnection(mock(WebSocketSession.class),
                        new NodeSecurityProperties());
        org.springframework.test.util.ReflectionTestUtils.setField(connection, "authenticated", true);
        return connection;
    }

    private JudgeNodeWebSocketHandler.NodeConnection connectionWithQuota(int quota) {
        JudgeNodeWebSocketHandler.NodeConnection connection = connection();
        org.springframework.test.util.ReflectionTestUtils.setField(connection, "maxConcurrency", quota);
        return connection;
    }

    @Test
    void sameRequestIdWithDifferentPayloadIsRejected() {
        JudgeNodeWebSocketHandler.NodeConnection connection = connection();
        assertThat(connection.acceptRequest("req-1", "hash-a")).isTrue();
        assertThat(connection.acceptRequest("req-1", "hash-a")).isTrue();
        assertThat(connection.acceptRequest("req-1", "hash-b")).isFalse();
    }

    @Test
    void replayLedgerIsBounded() {
        JudgeNodeWebSocketHandler.NodeConnection connection = connection();
        for (int i = 0; i < 2000; i++) {
            assertThat(connection.acceptRequest("req-" + i, "hash-" + i)).isTrue();
        }
        // 最早条目被有界 LRU 驱逐：重新出现视为新请求（不再无限占用内存）。
        assertThat(connection.acceptRequest("req-0", "hash-changed")).isTrue();
    }

    @Test
    void authResponseLedgerIsNamespacedFromOrdinaryRequests() {
        JudgeNodeWebSocketHandler.NodeConnection connection = connection();
        // 合法刷新：AUTH_REFRESH(id) 与随后同 id 的 AUTH_RESPONSE(id) 属于不同命名空间，均须放行。
        assertThat(connection.acceptRequest("shared-id", "AUTH_REFRESH\n{}")).isTrue();
        assertThat(connection.acceptAuthResponse("shared-id", "AUTH_RESPONSE\n{sig-a}")).isTrue();
        // 同一 id 的认证响应内容变化（挑战/签名不同）必须拒绝。
        assertThat(connection.acceptAuthResponse("shared-id", "AUTH_RESPONSE\n{sig-b}")).isFalse();
        // 普通业务消息仍按 id + type/payload 判重，不受认证命名空间影响。
        assertThat(connection.acceptRequest("shared-id", "AUTH_REFRESH\n{}")).isTrue();
        assertThat(connection.acceptRequest("shared-id", "HEARTBEAT\n{}")).isFalse();
        // 两类都加命名空间：普通消息 id 即使形如认证前缀也不与认证响应串扰。
        assertThat(connection.acceptAuthResponse("x", "AUTH_RESPONSE\n{a}")).isTrue();
        assertThat(connection.acceptRequest("auth\u0000x", "HEARTBEAT\n{}")).isTrue();
    }

    @Test
    void readyCreditIsConsumedAndNotRestoredByAck() {
        JudgeNodeWebSocketHandler.NodeConnection connection = connection();
        assertThat(connection.applyReady(1)).isTrue();
        assertThat(connection.isReady()).isTrue();
        assertThat(connection.beginAssignment("s1", "t1", "a1")).isTrue();
        assertThat(connection.availableSlots()).isZero();
        // TASK_ACK 不恢复 credit：未再次 READY 前不能分配第二个任务。
        assertThat(connection.isReady()).isFalse();
        assertThat(connection.acknowledgeAttempt("s1", "t1", "a1")).isTrue();
        assertThat(connection.beginAssignment("s2", "t2", "a2")).isFalse();
    }

    @Test
    void readyHintCannotExceedDbApprovedQuota() {
        JudgeNodeWebSocketHandler.NodeConnection connection = connection();
        connection.applyReady(100);
        assertThat(connection.availableSlots()).isEqualTo(1);
    }

    @Test
    void unackedAssignmentIsDeductedFromNextReady() {
        JudgeNodeWebSocketHandler.NodeConnection connection = connection();
        // READY(1) -> ASSIGN 未 ACK：再次 READY(1) 必须扣减未 ACK 占用，不得再分配第二个任务。
        connection.applyReady(1);
        assertThat(connection.beginAssignment("s1", "t1", "a1")).isTrue();
        assertThat(connection.pendingAckCount()).isEqualTo(1);
        connection.applyReady(1);
        assertThat(connection.availableSlots()).isZero();
        assertThat(connection.beginAssignment("s2", "t2", "a2")).isFalse();
    }

    @Test
    void readyTwoAllowsTwoAndAckReleasesPendingWithoutCredit() {
        JudgeNodeWebSocketHandler.NodeConnection connection = connectionWithQuota(2);
        // READY(2) 允许两个任务；ACK 前不得因再次 READY 重复授予。
        assertThat(connection.applyReady(2)).isTrue();
        assertThat(connection.beginAssignment("s1", "t1", "a1")).isTrue();
        assertThat(connection.beginAssignment("s2", "t2", "a2")).isTrue();
        assertThat(connection.beginAssignment("s3", "t3", "a3")).isFalse();
        assertThat(connection.pendingAckCount()).isEqualTo(2);
        // ACK 释放 pending，但不自行补回 credit。
        assertThat(connection.acknowledgeAttempt("s1", "t1", "a1")).isTrue();
        assertThat(connection.acknowledgeAttempt("s2", "t2", "a2")).isTrue();
        assertThat(connection.pendingAckCount()).isZero();
        assertThat(connection.availableSlots()).isZero();
        // ACK 后新的 READY 才能重新接纳任务。
        assertThat(connection.applyReady(1)).isTrue();
        assertThat(connection.beginAssignment("s3", "t3", "a3")).isTrue();
    }

    @Test
    void unknownOrDuplicateAckNeverAddsCredit() {
        JudgeNodeWebSocketHandler.NodeConnection connection = connection();
        connection.applyReady(1);
        assertThat(connection.beginAssignment("s1", "t1", "a1")).isTrue();
        assertThat(connection.acknowledgeAttempt("s1", "t1", "a1")).isTrue();
        // 重复/未知 ACK 不命中记录，绝不增加可用额度。
        assertThat(connection.acknowledgeAttempt("s1", "t1", "a1")).isFalse();
        assertThat(connection.acknowledgeAttempt("s9", "t9", "a9")).isFalse();
        assertThat(connection.availableSlots()).isZero();
        assertThat(connection.beginAssignment("s2", "t2", "a2")).isFalse();
    }

    @Test
    void terminalRemovalClearsPendingAck() {
        JudgeNodeWebSocketHandler.NodeConnection connection = connection();
        connection.applyReady(1);
        assertThat(connection.beginAssignment("s1", "t1", "a1")).isTrue();
        // 终态清理匹配状态后，新的 READY 不再被旧的未 ACK 记录占用。
        connection.removeAttempt("s1", "t1", "a1");
        assertThat(connection.pendingAckCount()).isZero();
        assertThat(connection.applyReady(1)).isTrue();
        assertThat(connection.beginAssignment("s2", "t2", "a2")).isTrue();
    }

    @Test
    void resumedAttemptsDoNotOccupyReadyCredit() {
        JudgeNodeWebSocketHandler.NodeConnection connection = connectionWithQuota(2);
        // RESUME 恢复的是已 ACK 的运行中任务，只登记精确 cancel，不占用未 ACK credit。
        connection.addAttempt("s1", "t1", "a1");
        assertThat(connection.pendingAckCount()).isZero();
        assertThat(connection.applyReady(1)).isTrue();
        assertThat(connection.beginAssignment("s2", "t2", "a2")).isTrue();
    }

    @Test
    void drainClearsCreditAndCannotBeReenabledByReady() {
        JudgeNodeWebSocketHandler.NodeConnection connection = connection();
        connection.applyReady(1);
        connection.markDraining();
        assertThat(connection.isReady()).isFalse();
        assertThat(connection.applyReady(5)).isFalse();
        assertThat(connection.availableSlots()).isZero();
    }

    @Test
    void terminalAndProgressFramesAreDiscriminated() {
        assertThat(JudgeNodeWebSocketHandler.acceptedByFrame(true, "JUDGE_FINISHED")).isTrue();
        assertThat(JudgeNodeWebSocketHandler.acceptedByFrame(true, "JUDGE_FAILED")).isTrue();
        // TASK_RESULT 携带 STATUS_CHANGED 必须被拒绝，不能回 TASK_RESULT_ACK。
        assertThat(JudgeNodeWebSocketHandler.acceptedByFrame(true, "STATUS_CHANGED")).isFalse();
        assertThat(JudgeNodeWebSocketHandler.acceptedByFrame(false, "STATUS_CHANGED")).isTrue();
        assertThat(JudgeNodeWebSocketHandler.acceptedByFrame(false, "CASE_FINISHED")).isTrue();
        assertThat(JudgeNodeWebSocketHandler.acceptedByFrame(false, "JUDGE_FINISHED")).isFalse();
        assertThat(JudgeNodeWebSocketHandler.acceptedByFrame(false, null)).isFalse();
    }

    @Test
    void activeAttemptsAreTrackedAndRemovedForPreciseCancel() {
        JudgeNodeWebSocketHandler.NodeConnection connection = connection();
        connection.addAttempt("s1", "t1", "a1");
        connection.addAttempt("s2", "t2", "a2");
        List<Map<String, Object>> attempts = connection.snapshotAttempts();
        assertThat(attempts).hasSize(2);
        connection.removeAttempt("s1", "t1", "a1");
        assertThat(connection.snapshotAttempts()).hasSize(1);
        assertThat(connection.snapshotAttempts().get(0)).containsEntry("submissionId", "s2");
    }

    @Test
    void connectionCapacityIsEnforcedAtomicallyAndReleasedOnce() throws Exception {
        NodeSecurityProperties properties = new NodeSecurityProperties();
        properties.setMaxConnections(1);
        NodeIdentityService identity = mock(NodeIdentityService.class);
        when(identity.createAuthChallenge(any(), isNull(), isNull(), any(), eq(false)))
                .thenReturn(new NodeAuthChallengeVo("c", "n", "aud",
                        System.currentTimeMillis() + 30_000L, System.currentTimeMillis(), "r"));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            JudgeNodeWebSocketHandler handler = new JudgeNodeWebSocketHandler(identity, properties,
                    new ObjectMapper(), scheduler, mock(JudgeTaskClaimService.class),
                    mock(JudgeResultReportService.class), mock(JudgeNodeLifecycleService.class),
                    mock(StringRedisTemplate.class));

            WebSocketSession first = openSession("s1");
            handler.afterConnectionEstablished(first);
            WebSocketSession second = openSession("s2");
            handler.afterConnectionEstablished(second);
            // 容量超限：第二个连接被拒绝（SERVICE_OVERLOAD），未占用许可。
            verify(second).close(any(CloseStatus.class));

            // 释放第一个连接后，容量可被新连接取得；不会重复释放导致计数漂移。
            handler.afterConnectionClosed(first, CloseStatus.NORMAL);
            WebSocketSession third = openSession("s3");
            handler.afterConnectionEstablished(third);
            verify(third, never()).close(any(CloseStatus.class));
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void reaperNotifiesDrainingOnceWithoutClientHeartbeatOrReady() throws Exception {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2);
        scheduler.setRemoveOnCancelPolicy(true);
        NodeIdentityService identity = mock(NodeIdentityService.class);
        when(identity.createAuthChallenge(any(), isNull(), isNull(), any(), eq(false)))
                .thenReturn(new NodeAuthChallengeVo("c", "n", "aud",
                        System.currentTimeMillis() + 30_000L, System.currentTimeMillis(), "r"));
        JudgeNodeToken draining = new JudgeNodeToken();
        draining.setStatus(NodeProtocolConstants.NODE_STATUS_DRAINING);
        draining.setDraining(true);
        draining.setSessionEpoch(1L);
        draining.setAccessVersion(1);
        when(identity.findNode("node-drain")).thenReturn(draining);

        List<String> frames = new CopyOnWriteArrayList<>();
        WebSocketSession session = frameCapturingSession("peer-conn", frames);
        JudgeNodeWebSocketHandler handler = handler(identity, scheduler);
        try {
            handler.afterConnectionEstablished(session);
            JudgeNodeWebSocketHandler.NodeConnection connection = handler.connectionOf("peer-conn");
            authenticateConnection(connection, "node-drain");
            // 客户端未发 heartbeat/READY：跨实例 drain 必须由 reaper 主动同步并停止调度。
            handler.startReaper();
            waitForFrame(frames, NodeProtocolConstants.WS_NODE_STATE, 5_000L);
            Thread.sleep(1_500L);
            // 状态只改变一次，巡检不得刷屏重复推送。
            long stateFrames = frames.stream()
                    .filter(frame -> frame.contains("\"" + NodeProtocolConstants.WS_NODE_STATE + "\""))
                    .count();
            assertThat(stateFrames).isEqualTo(1);
            assertThat(frames.stream().anyMatch(frame -> frame.contains("\"draining\":true"))).isTrue();
            assertThat(connection.isReady()).isFalse();
        } finally {
            handler.shutdown();
            scheduler.shutdownNow();
        }
    }

    @Test
    void authOnDrainingNodePushesAuthoritativeNodeState() throws Exception {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2);
        scheduler.setRemoveOnCancelPolicy(true);
        NodeIdentityService identity = mock(NodeIdentityService.class);
        when(identity.createAuthChallenge(any(), isNull(), isNull(), any(), eq(false)))
                .thenReturn(new NodeAuthChallengeVo("c", "n", "aud",
                        System.currentTimeMillis() + 30_000L, System.currentTimeMillis(), "r"));
        long now = System.currentTimeMillis();
        NodeAuthResult result = new NodeAuthResult();
        result.setNodeId("node-drain");
        result.setKeyId("key");
        result.setAccessToken("token");
        result.setAccessExpiresAt(now + 60_000L);
        result.setSessionEpoch(2L);
        result.setServerTime(now);
        result.setMaxConcurrency(2);
        result.setSupportedJudgeModes(List.of("default"));
        result.setAuthorizationUntil(now + 60_000L);
        result.setHeartbeatIntervalMillis(5_000L);
        result.setAccessVersion(1);
        result.setWeight(10);
        result.setStatus(NodeProtocolConstants.NODE_STATUS_DRAINING);
        result.setDraining(true);
        when(identity.authenticate(any(), any(), any(), any(), any(), any())).thenReturn(result);

        List<String> frames = new CopyOnWriteArrayList<>();
        WebSocketSession session = frameCapturingSession("reauth-conn", frames);
        JudgeNodeWebSocketHandler handler = handler(identity, scheduler);
        try {
            handler.afterConnectionEstablished(session);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("nodeId", "node-drain");
            payload.put("keyId", "key");
            payload.put("challengeId", "challenge");
            payload.put("signature", "sig");
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("version", NodeProtocolConstants.PROTOCOL_VERSION);
            envelope.put("type", NodeProtocolConstants.WS_AUTH_RESPONSE);
            envelope.put("requestId", "auth-1");
            envelope.put("timestamp", System.currentTimeMillis());
            envelope.put("payload", payload);
            handler.handleTextMessage(session,
                    new TextMessage(new ObjectMapper().writeValueAsString(envelope)));

            // 初始认证成功后必须通过既有 NODE_STATE 暴露权威排空状态，使重连即可观察。
            waitForFrame(frames, NodeProtocolConstants.WS_AUTH_OK, 5_000L);
            waitForFrame(frames, NodeProtocolConstants.WS_NODE_STATE, 5_000L);
            assertThat(frames.stream().anyMatch(frame -> frame.contains("\"draining\":true"))).isTrue();
            assertThat(handler.connectionOf("reauth-conn").isReady()).isFalse();
        } finally {
            handler.shutdown();
            scheduler.shutdownNow();
        }
    }

    @Test
    void activeAuthPushesAuthoritativeActiveStateAndHeartbeatDoesNotRepeat() throws Exception {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2);
        scheduler.setRemoveOnCancelPolicy(true);
        NodeIdentityService identity = mock(NodeIdentityService.class);
        when(identity.createAuthChallenge(any(), isNull(), isNull(), any(), eq(false))).thenReturn(challenge());
        long now = System.currentTimeMillis();
        when(identity.authenticate(any(), any(), any(), any(), any(), any()))
                .thenReturn(authResult("node-active", now, false, NodeProtocolConstants.NODE_STATUS_ACTIVE));
        when(identity.requireSessionOwner(any(), any(), anyLong(), any(), anyBoolean()))
                .thenReturn(node("node-active", NodeProtocolConstants.NODE_STATUS_ACTIVE, false));

        List<String> frames = new CopyOnWriteArrayList<>();
        WebSocketSession session = frameCapturingSession("active-conn", frames);
        JudgeNodeWebSocketHandler handler = handler(identity, scheduler);
        try {
            handler.afterConnectionEstablished(session);
            deliver(handler, session, NodeProtocolConstants.WS_AUTH_RESPONSE, "auth-active",
                    authPayload("node-active"));
            waitForFrame(frames, NodeProtocolConstants.WS_AUTH_OK, 5_000L);
            waitForFrame(frames, NodeProtocolConstants.WS_NODE_STATE, 5_000L);
            // 顺序必须是 AUTH_OK -> NODE_STATE，且 active 是权威状态。
            assertThat(indexOfType(frames, NodeProtocolConstants.WS_AUTH_OK))
                    .isLessThan(indexOfType(frames, NodeProtocolConstants.WS_NODE_STATE));
            assertThat(frames.stream().anyMatch(frame -> frame.contains("\"status\":\"active\""))).isTrue();
            assertThat(frames.stream().anyMatch(frame -> frame.contains("\"draining\":false"))).isTrue();
            // 已同步 active 后普通 HEARTBEAT 不得重复推送 NODE_STATE。
            deliver(handler, session, NodeProtocolConstants.WS_HEARTBEAT, "hb-active", Map.of("runningTasks", 0));
            waitForFrame(frames, NodeProtocolConstants.WS_HEARTBEAT_ACK, 5_000L);
            Thread.sleep(300L);
            assertThat(countType(frames, NodeProtocolConstants.WS_NODE_STATE)).isEqualTo(1L);
        } finally {
            handler.shutdown();
            scheduler.shutdownNow();
        }
    }

    @Test
    void refreshAuthRePushesAuthoritativeNodeState() throws Exception {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2);
        scheduler.setRemoveOnCancelPolicy(true);
        NodeIdentityService identity = mock(NodeIdentityService.class);
        when(identity.createAuthChallenge(any(), isNull(), isNull(), any(), eq(false))).thenReturn(challenge());
        when(identity.createAuthChallenge(any(), any(), any(), any(), eq(true))).thenReturn(challenge());
        long now = System.currentTimeMillis();
        when(identity.authenticate(any(), any(), any(), any(), any(), any()))
                .thenReturn(authResult("node-refresh", now, false, NodeProtocolConstants.NODE_STATUS_ACTIVE));

        List<String> frames = new CopyOnWriteArrayList<>();
        WebSocketSession session = frameCapturingSession("refresh-conn", frames);
        JudgeNodeWebSocketHandler handler = handler(identity, scheduler);
        try {
            handler.afterConnectionEstablished(session);
            deliver(handler, session, NodeProtocolConstants.WS_AUTH_RESPONSE, "auth-refresh-1",
                    authPayload("node-refresh"));
            waitForFrame(frames, NodeProtocolConstants.WS_AUTH_OK, 5_000L);
            waitForFrame(frames, NodeProtocolConstants.WS_NODE_STATE, 5_000L);

            deliver(handler, session, NodeProtocolConstants.WS_AUTH_REFRESH, "refresh-1", Map.of());
            waitForCount(frames, NodeProtocolConstants.WS_AUTH_CHALLENGE, 2L, 5_000L);
            deliver(handler, session, NodeProtocolConstants.WS_AUTH_RESPONSE, "auth-refresh-2",
                    authPayload("node-refresh"));
            waitForCount(frames, NodeProtocolConstants.WS_AUTH_OK, 2L, 5_000L);
            // 刷新同样必须在 AUTH_OK 之后重发权威 NODE_STATE。
            waitForCount(frames, NodeProtocolConstants.WS_NODE_STATE, 2L, 5_000L);
            assertThat(countType(frames, NodeProtocolConstants.WS_AUTH_OK)).isEqualTo(2L);
        } finally {
            handler.shutdown();
            scheduler.shutdownNow();
        }
    }

    @Test
    void refreshAuthReusingChallengeRequestIdSucceedsAndKeepsEpochActive() throws Exception {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2);
        scheduler.setRemoveOnCancelPolicy(true);
        NodeIdentityService identity = mock(NodeIdentityService.class);
        when(identity.createAuthChallenge(any(), isNull(), isNull(), any(), eq(false))).thenReturn(challenge());
        when(identity.createAuthChallenge(any(), any(), any(), any(), eq(true))).thenReturn(challenge());
        long now = System.currentTimeMillis();
        when(identity.authenticate(any(), any(), any(), any(), any(), any()))
                .thenReturn(authResult("node-refresh-same-id", now, false,
                        NodeProtocolConstants.NODE_STATUS_ACTIVE));

        List<String> frames = new CopyOnWriteArrayList<>();
        WebSocketSession session = frameCapturingSession("refresh-same-id-conn", frames);
        JudgeNodeWebSocketHandler handler = handler(identity, scheduler);
        try {
            handler.afterConnectionEstablished(session);
            // 初始认证使用独立 requestId。
            deliver(handler, session, NodeProtocolConstants.WS_AUTH_RESPONSE, "initial-id",
                    authPayload("node-refresh-same-id"));
            waitForFrame(frames, NodeProtocolConstants.WS_AUTH_OK, 5_000L);
            waitForFrame(frames, NodeProtocolConstants.WS_NODE_STATE, 5_000L);
            long epoch = handler.connectionOf("refresh-same-id-conn").sessionEpoch();

            // 探针真实流程：AUTH_REFRESH(id) -> AUTH_CHALLENGE(id) -> AUTH_RESPONSE(id) 复用同一 requestId。
            deliver(handler, session, NodeProtocolConstants.WS_AUTH_REFRESH, "shared-id", Map.of());
            waitForCount(frames, NodeProtocolConstants.WS_AUTH_CHALLENGE, 2L, 5_000L);
            deliver(handler, session, NodeProtocolConstants.WS_AUTH_RESPONSE, "shared-id",
                    authPayload("node-refresh-same-id"));
            waitForCount(frames, NodeProtocolConstants.WS_AUTH_OK, 2L, 5_000L);
            waitForCount(frames, NodeProtocolConstants.WS_NODE_STATE, 2L, 5_000L);

            // 合法刷新不得报重复 requestId；sessionEpoch 与 ACTIVE 状态保持不变。
            assertThat(countType(frames, NodeProtocolConstants.WS_ERROR)).isZero();
            assertThat(handler.connectionOf("refresh-same-id-conn").sessionEpoch()).isEqualTo(epoch);
            assertThat(frames.stream().anyMatch(frame -> frame.contains("\"status\":\"active\""))).isTrue();
            assertThat(frames.stream().noneMatch(frame -> frame.contains("\"draining\":true"))).isTrue();
        } finally {
            handler.shutdown();
            scheduler.shutdownNow();
        }
    }

    @Test
    void temporaryNodeHardExpiryCancelsAttemptAndNotifiesBeforeClose() throws Exception {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2);
        scheduler.setRemoveOnCancelPolicy(true);
        NodeIdentityService identity = mock(NodeIdentityService.class);
        when(identity.createAuthChallenge(any(), isNull(), isNull(), any(), eq(false))).thenReturn(challenge());
        long now = System.currentTimeMillis();
        when(identity.findNode("node-temp")).thenReturn(hardExpiredNode("node-temp", now));

        List<String> events = new CopyOnWriteArrayList<>();
        WebSocketSession session = orderedSession("temp-conn", events);
        JudgeNodeWebSocketHandler handler = handler(identity, scheduler);
        try {
            handler.afterConnectionEstablished(session);
            JudgeNodeWebSocketHandler.NodeConnection connection = handler.connectionOf("temp-conn");
            authenticateConnection(connection, "node-temp");
            org.springframework.test.util.ReflectionTestUtils.setField(connection, "accessExpiresAt", now - 1_000L);
            connection.addAttempt("s1", "t1", "a1");

            handler.startReaper();
            waitForFrame(events, NodeProtocolConstants.WS_TASK_CANCEL, 5_000L);
            waitForFrame(events, NodeProtocolConstants.WS_NODE_STATE, 5_000L);
            waitForClose(events, 5_000L);

            // 终态通知必须先于关闭：精确取消在途 attempt 且状态为 expired。
            assertThat(events.stream().anyMatch(event -> event.contains("\"a1\""))).isTrue();
            assertThat(events.stream().anyMatch(event -> event.contains("\"status\":\"expired\""))).isTrue();
            assertThat(indexOfType(events, NodeProtocolConstants.WS_TASK_CANCEL))
                    .isLessThan(indexOfType(events, NodeProtocolConstants.WS_NODE_STATE));
            assertThat(indexOfType(events, NodeProtocolConstants.WS_NODE_STATE))
                    .isLessThan(indexOfText(events, CLOSE_EVENT));
        } finally {
            handler.shutdown();
            scheduler.shutdownNow();
        }
    }

    @Test
    void inboundMessageOnHardExpiredNodeDoesNotPreemptTerminalNotification() throws Exception {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2);
        scheduler.setRemoveOnCancelPolicy(true);
        NodeIdentityService identity = mock(NodeIdentityService.class);
        when(identity.createAuthChallenge(any(), isNull(), isNull(), any(), eq(false))).thenReturn(challenge());
        long now = System.currentTimeMillis();
        when(identity.findNode("node-temp-inbound")).thenReturn(hardExpiredNode("node-temp-inbound", now));

        List<String> events = new CopyOnWriteArrayList<>();
        WebSocketSession session = orderedSession("inbound-conn", events);
        JudgeNodeWebSocketHandler handler = handler(identity, scheduler);
        try {
            handler.afterConnectionEstablished(session);
            JudgeNodeWebSocketHandler.NodeConnection connection = handler.connectionOf("inbound-conn");
            authenticateConnection(connection, "node-temp-inbound");
            org.springframework.test.util.ReflectionTestUtils.setField(connection, "accessExpiresAt", now - 1_000L);
            connection.addAttempt("s1", "t1", "a1");

            // 令牌过期后的入站业务消息被拒绝，但不得抢先关闭：终态取消与状态仍须先发出。
            deliver(handler, session, NodeProtocolConstants.WS_HEARTBEAT, "expired-hb",
                    Map.of("runningTasks", 0));
            waitForFrame(events, NodeProtocolConstants.WS_TASK_CANCEL, 5_000L);
            waitForFrame(events, NodeProtocolConstants.WS_NODE_STATE, 5_000L);
            waitForClose(events, 5_000L);
            assertThat(events.stream().anyMatch(event -> event.contains("\"status\":\"expired\""))).isTrue();
            assertThat(indexOfType(events, NodeProtocolConstants.WS_TASK_CANCEL))
                    .isLessThan(indexOfText(events, CLOSE_EVENT));
        } finally {
            handler.shutdown();
            scheduler.shutdownNow();
        }
    }

    @Test
    void formalNodeOnlyTokenExpiryClosesWithoutCancellingValidAttempt() throws Exception {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2);
        scheduler.setRemoveOnCancelPolicy(true);
        NodeIdentityService identity = mock(NodeIdentityService.class);
        when(identity.createAuthChallenge(any(), isNull(), isNull(), any(), eq(false))).thenReturn(challenge());
        when(identity.findNode("node-formal")).thenReturn(
                node("node-formal", NodeProtocolConstants.NODE_STATUS_ACTIVE, false));

        List<String> events = new CopyOnWriteArrayList<>();
        WebSocketSession session = orderedSession("formal-conn", events);
        JudgeNodeWebSocketHandler handler = handler(identity, scheduler);
        try {
            handler.afterConnectionEstablished(session);
            JudgeNodeWebSocketHandler.NodeConnection connection = handler.connectionOf("formal-conn");
            authenticateConnection(connection, "node-formal");
            org.springframework.test.util.ReflectionTestUtils.setField(connection,
                    "accessExpiresAt", System.currentTimeMillis() - 1_000L);
            connection.addAttempt("s1", "t1", "a1");

            handler.startReaper();
            waitForClose(events, 5_000L);
            // 仅短期令牌过期：只关连接，不取消在途任务、不推送节点终态、不写节点身份。
            assertThat(countType(events, NodeProtocolConstants.WS_TASK_CANCEL)).isZero();
            assertThat(countType(events, NodeProtocolConstants.WS_NODE_STATE)).isZero();
            verify(identity, never()).requireUsableKey(any(), any(), anyBoolean());
        } finally {
            handler.shutdown();
            scheduler.shutdownNow();
        }
    }

    private static final String CLOSE_EVENT = "__CLOSE__";

    private JudgeNodeToken hardExpiredNode(String nodeId, long now) {
        JudgeNodeToken node = node(nodeId, NodeProtocolConstants.NODE_STATUS_ACTIVE, false);
        node.setAuthorizationUntil(java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(now - 60_000L), java.time.ZoneOffset.ofHours(8)));
        return node;
    }

    private WebSocketSession orderedSession(String id, List<String> events) throws java.io.IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            events.add(((TextMessage) invocation.getArgument(0)).getPayload());
            return null;
        }).when(session).sendMessage(any());
        doAnswer(invocation -> {
            events.add(CLOSE_EVENT);
            return null;
        }).when(session).close();
        return session;
    }

    private void waitForClose(List<String> events, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (events.contains(CLOSE_EVENT)) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("timed out waiting for session close, received=" + events);
    }

    private int indexOfText(List<String> events, String text) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).contains(text)) {
                return i;
            }
        }
        return -1;
    }

    private JudgeNodeWebSocketHandler handler(NodeIdentityService identity, ScheduledExecutorService scheduler) {
        return new JudgeNodeWebSocketHandler(identity, new NodeSecurityProperties(),
                new ObjectMapper(), scheduler, mock(JudgeTaskClaimService.class),
                mock(JudgeResultReportService.class), mock(JudgeNodeLifecycleService.class),
                mock(StringRedisTemplate.class));
    }

    private WebSocketSession frameCapturingSession(String id, List<String> frames) throws java.io.IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            frames.add(((TextMessage) invocation.getArgument(0)).getPayload());
            return null;
        }).when(session).sendMessage(any());
        return session;
    }

    private void authenticateConnection(JudgeNodeWebSocketHandler.NodeConnection connection, String nodeId) {
        org.springframework.test.util.ReflectionTestUtils.setField(connection, "authenticated", true);
        org.springframework.test.util.ReflectionTestUtils.setField(connection, "nodeId", nodeId);
        org.springframework.test.util.ReflectionTestUtils.setField(connection, "keyId", "key");
        org.springframework.test.util.ReflectionTestUtils.setField(connection, "sessionEpoch", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(connection, "accessVersion", 1);
    }

    private void waitForFrame(List<String> frames, String type, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (frames.stream().anyMatch(frame -> frame.contains("\"" + type + "\""))) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("timed out waiting for frame type " + type + ", received=" + frames);
    }

    private NodeAuthChallengeVo challenge() {
        return new NodeAuthChallengeVo("c", "n", "aud",
                System.currentTimeMillis() + 30_000L, System.currentTimeMillis(), "r");
    }

    private NodeAuthResult authResult(String nodeId, long now, boolean draining, String status) {
        NodeAuthResult result = new NodeAuthResult();
        result.setNodeId(nodeId);
        result.setKeyId("key");
        result.setAccessToken("token");
        result.setAccessExpiresAt(now + 60_000L);
        result.setSessionEpoch(2L);
        result.setServerTime(now);
        result.setMaxConcurrency(2);
        result.setSupportedJudgeModes(List.of("default"));
        result.setAuthorizationUntil(now + 60_000L);
        result.setHeartbeatIntervalMillis(5_000L);
        result.setAccessVersion(1);
        result.setWeight(10);
        result.setStatus(status);
        result.setDraining(draining);
        return result;
    }

    private JudgeNodeToken node(String nodeId, String status, boolean draining) {
        JudgeNodeToken node = new JudgeNodeToken();
        node.setNodeId(nodeId);
        node.setStatus(status);
        node.setDraining(draining);
        node.setSessionEpoch(2L);
        node.setAccessVersion(1);
        return node;
    }

    private Map<String, Object> authPayload(String nodeId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("keyId", "key");
        payload.put("challengeId", "challenge");
        payload.put("signature", "sig");
        return payload;
    }

    private void deliver(JudgeNodeWebSocketHandler handler, WebSocketSession session, String type,
                         String requestId, Map<String, Object> payload) throws Exception {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("version", NodeProtocolConstants.PROTOCOL_VERSION);
        envelope.put("type", type);
        envelope.put("requestId", requestId);
        envelope.put("timestamp", System.currentTimeMillis());
        envelope.put("payload", payload);
        handler.handleTextMessage(session,
                new TextMessage(new ObjectMapper().writeValueAsString(envelope)));
    }

    private long countType(List<String> frames, String type) {
        return frames.stream().filter(frame -> frame.contains("\"" + type + "\"")).count();
    }

    private int indexOfType(List<String> frames, String type) {
        for (int i = 0; i < frames.size(); i++) {
            if (frames.get(i).contains("\"" + type + "\"")) {
                return i;
            }
        }
        return -1;
    }

    private void waitForCount(List<String> frames, String type, long count, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (countType(frames, type) >= count) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("timed out waiting for " + count + " " + type + " frames, received=" + frames);
    }

    private WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
