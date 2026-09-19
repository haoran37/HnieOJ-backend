package com.hnieacm.judge.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.judge.NodeProtocolConstants;
import com.hnieacm.common.judge.NodeSignatureCodec;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.dto.NodeAuthChallengeVo;
import com.hnieacm.judge.dto.NodeAuthResult;
import com.hnieacm.judge.dto.NodeRuntimeMetrics;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.properties.NodeSecurityProperties;
import com.hnieacm.judge.service.JudgeNodeLifecycleService;
import com.hnieacm.judge.service.NodeIdentityService;
import com.hnieacm.judge.service.SignedCaller;
import com.hnieacm.submission.dto.JudgeResultEventRequest;
import com.hnieacm.submission.dto.JudgeTaskLeaseRequest;
import com.hnieacm.submission.dto.JudgeTaskResumeRequest;
import com.hnieacm.submission.service.JudgeResultReportService;
import com.hnieacm.submission.service.JudgeTaskClaimService;
import com.hnieacm.submission.vo.JudgeTaskClaimVo;
import com.hnieacm.submission.vo.JudgeTaskLeaseVo;
import com.hnieacm.submission.vo.JudgeTaskResumeVo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 节点协议 v1 WebSocket 处理器。
 *
 * <p>连接建立后下发一次性认证挑战，校验 Ed25519 证明后签发短期令牌；
 * 数据库 sessionEpoch / 节点状态 / accessVersion 为权威，旧连接即使未关闭也会在业务消息上被拒绝。
 * 认证成功前不接受 READY/RESUME，未成功 RESUME 不允许调度。任务业务全部委托
 * {@link JudgeTaskClaimService} / {@link JudgeResultReportService}，handler 只做协议解析与消息回写。
 * 每个连接使用独立的有界串行 writer，避免慢 socket 阻塞共享调度/容器线程；
 * 容量获取原子化，连接关停有独立于 writer 的硬截止。</p>
 *
 * @author Codex
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeNodeWebSocketHandler extends TextWebSocketHandler {

    private static final int WRITER_SEND_TIME_LIMIT_MS = 10_000;
    private static final long REAPER_INTERVAL_MILLIS = 1_000L;
    private static final long MIN_MESSAGE_RATE_WINDOW_SECONDS = 1L;
    private static final int DEFAULT_AVAILABLE_SLOTS = 1;
    private static final int DEFAULT_WEIGHT = 10;

    /** 无重放冲突窗口：有界 LRU，断线随连接销毁，绝不无限增长。 */
    private static final int REPLAY_LEDGER_MAX = 512;

    /**
     * 防重放账本命名空间：认证响应与普通业务消息分开，避免同一 requestId 跨类别误判。
     * 两类都加前缀，客户端任意 requestId（即使自带 "auth:" 字样）也不会与另一命名空间串扰。
     */
    private static final String REQUEST_LEDGER_AUTH_PREFIX = "auth\u0000";
    private static final String REQUEST_LEDGER_MESSAGE_PREFIX = "message\u0000";

    /** 连接级在途 attempt 上限，仅用于精确 TASK_CANCEL。 */
    private static final int ACTIVE_ATTEMPT_MAX = 512;

    /** RESUME 单次最多接受的 attempt 数，防止超大数组拖垮事务。 */
    private static final int MAX_RESUME_ATTEMPTS = 256;

    /** 权威状态变更后先给 writer 机会写出取消/状态，再由独立硬超时关闭。 */
    private static final long CANCEL_CLOSE_GRACE_MILLIS = 2_000L;

    /** 已认证连接权威状态复查间隔，跨实例管理变更据此在有限时间内收敛。 */
    private static final long VALIDITY_CHECK_INTERVAL_MILLIS = 1_000L;

    private static final String EVENT_STATUS_CHANGED = "STATUS_CHANGED";
    private static final String EVENT_CASE_FINISHED = "CASE_FINISHED";
    private static final String EVENT_JUDGE_FINISHED = "JUDGE_FINISHED";
    private static final String EVENT_JUDGE_FAILED = "JUDGE_FAILED";
    private static final Set<String> TERMINAL_EVENT_TYPES = Set.of(EVENT_JUDGE_FINISHED, EVENT_JUDGE_FAILED);
    private static final Set<String> PROGRESS_EVENT_TYPES = Set.of(EVENT_STATUS_CHANGED, EVENT_CASE_FINISHED);

    private static final long MAX_RUNNING_TASKS = 100_000L;
    private static final int MAX_CPU_CORE = 4096;
    private static final long MAX_STORAGE_BYTES = 1L << 60;
    private static final int MAX_CACHE_PROBLEM_COUNT = 1_000_000;
    private static final int MAX_VERSION_LENGTH = 100;

    private static final Set<String> SERVER_ONLY_TYPES = Set.of(
            NodeProtocolConstants.WS_AUTH_CHALLENGE,
            NodeProtocolConstants.WS_AUTH_OK,
            NodeProtocolConstants.WS_RESUME_RESULT,
            NodeProtocolConstants.WS_TASK_ASSIGN,
            NodeProtocolConstants.WS_TASK_EVENT_ACK,
            NodeProtocolConstants.WS_TASK_RESULT_ACK,
            NodeProtocolConstants.WS_LEASE_RENEWED,
            NodeProtocolConstants.WS_TASK_CANCEL,
            NodeProtocolConstants.WS_HEARTBEAT_ACK,
            NodeProtocolConstants.WS_NODE_STATE,
            NodeProtocolConstants.WS_ERROR,
            NodeProtocolConstants.WS_PONG);

    /** 可能携带判题结果正文的消息类型，允许使用较大的 task 帧上限。 */
    private static final Set<String> BULK_INBOUND_TYPES = Set.of(
            NodeProtocolConstants.WS_TASK_RUNNING,
            NodeProtocolConstants.WS_TASK_RESULT);

    /**
     * 节点目录 compare-delete：仅当当前值仍等于本连接写入的值时才删除，
     * 避免旧连接断开时误删新 owner 的目录项。
     */
    private static final RedisScript<Long> COMPARE_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /**
     * 节点目录 compare-refresh：仅当当前目录值仍是本连接写入的值时才续期 TTL，
     * 避免旧连接复写/续租已被新 owner 接管的目录项。
     */
    private static final RedisScript<Long> COMPARE_REFRESH_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('expire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    private final NodeIdentityService nodeIdentityService;
    private final NodeSecurityProperties nodeSecurityProperties;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService nodeWsScheduler;
    private final JudgeTaskClaimService judgeTaskClaimService;
    private final JudgeResultReportService judgeResultReportService;
    private final JudgeNodeLifecycleService judgeNodeLifecycleService;
    private final StringRedisTemplate stringRedisTemplate;

    private final Map<String, NodeConnection> connections = new ConcurrentHashMap<>();
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final String instanceId = UUID.randomUUID().toString().replace("-", "");

    @PostConstruct
    public void startReaper() {
        nodeWsScheduler.scheduleWithFixedDelay(this::reapConnections,
                REAPER_INTERVAL_MILLIS, REAPER_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        for (NodeConnection connection : connections.values()) {
            close(connection);
        }
        connections.clear();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        int maxConnections = nodeSecurityProperties.getMaxConnections();
        int current = activeConnections.incrementAndGet();
        if (maxConnections > 0 && current > maxConnections) {
            activeConnections.decrementAndGet();
            log.warn("judge node ws connection rejected: instance connection limit reached");
            try {
                session.close(CloseStatus.SERVICE_OVERLOAD);
            } catch (Exception ignored) {
                // 拒绝路径无需处理关闭异常
            }
            return;
        }
        WebSocketSession bounded = new ConcurrentWebSocketSessionDecorator(
                session, WRITER_SEND_TIME_LIMIT_MS, nodeSecurityProperties.getMaxTaskFrameBytes());
        NodeConnection connection = new NodeConnection(bounded, nodeSecurityProperties);
        connections.put(session.getId(), connection);
        sendChallenge(connection, UUID.randomUUID().toString(), false);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        NodeConnection connection = connections.get(session.getId());
        if (connection == null) {
            return;
        }
        connection.lastInboundAt = System.currentTimeMillis();
        if (message.getPayloadLength() > nodeSecurityProperties.getMaxTaskFrameBytes()) {
            rejectAndClose(connection, ResultCode.BAD_REQUEST, "消息超过大小上限");
            return;
        }
        if (!connection.allowInboundMessage()) {
            rejectAndClose(connection, ResultCode.BAD_REQUEST, "消息速率超限");
            return;
        }
        String requestId = null;
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            int version = root.path("version").asInt(0);
            String type = text(root, "type");
            requestId = text(root, "requestId");
            long timestamp = root.path("timestamp").asLong(0);
            if (version != NodeProtocolConstants.PROTOCOL_VERSION || type == null || requestId == null) {
                sendError(connection, requestId, ResultCode.BAD_REQUEST, "协议版本或字段非法", false);
                return;
            }
            if (!BULK_INBOUND_TYPES.contains(type)
                    && message.getPayloadLength() > nodeSecurityProperties.getMaxControlFrameBytes()) {
                sendError(connection, requestId, ResultCode.BAD_REQUEST, "控制消息超过大小上限", false);
                return;
            }
            if (Math.abs(System.currentTimeMillis() - timestamp)
                    > nodeSecurityProperties.getAllowedClockSkewSeconds() * 1000L) {
                sendError(connection, requestId, ResultCode.UNAUTHORIZED, "消息时间戳超出偏差", false);
                return;
            }
            if (SERVER_ONLY_TYPES.contains(type)) {
                sendError(connection, requestId, ResultCode.BAD_REQUEST, "非法客户端消息类型", false);
                return;
            }
            // requestId 有界防冲突：相同 id + 相同 type/payload 视为合法业务重试（幂等仍在业务层），
            // 相同 id 不同内容一律拒绝。指纹不含信封 timestamp/requestId，避免重试时间戳导致误判。
            // 认证响应走独立命名空间：合法刷新 AUTH_REFRESH(id) -> AUTH_RESPONSE(id) 复用同一 id，
            // 而普通业务消息之间仍按 id + type/payload 判重，认证响应内容变化也仍被拒绝。
            JsonNode payloadNode = root.path("payload");
            String fingerprint = NodeSignatureCodec.sha256Hex(
                    type + '\n' + (payloadNode.isMissingNode() ? "" : payloadNode.toString()));
            boolean accepted = NodeProtocolConstants.WS_AUTH_RESPONSE.equals(type)
                    ? connection.acceptAuthResponse(requestId, fingerprint)
                    : connection.acceptRequest(requestId, fingerprint);
            if (!accepted) {
                sendError(connection, requestId, ResultCode.BAD_REQUEST, "重复 requestId 内容不一致", false);
                return;
            }
            dispatch(connection, type, requestId, payloadNode);
        } catch (BizException e) {
            sendError(connection, requestId, e.getCode(), e.getMsg(), false);
        } catch (Exception e) {
            log.warn("judge node ws message failed, session {}: {}", session.getId(), e.getMessage());
            sendError(connection, requestId, ResultCode.INTERNAL_ERROR, "消息处理失败", true);
        }
    }

    private void dispatch(NodeConnection connection, String type, String requestId, JsonNode payload) {
        if (NodeProtocolConstants.WS_AUTH_RESPONSE.equals(type)) {
            handleAuthResponse(connection, requestId, payload);
        } else if (NodeProtocolConstants.WS_AUTH_REFRESH.equals(type)) {
            handleAuthRefresh(connection, requestId);
        } else if (NodeProtocolConstants.WS_PING.equals(type)) {
            send(connection, NodeProtocolConstants.WS_PONG, requestId, Map.of());
        } else {
            handleAuthenticated(connection, type, requestId, payload);
        }
    }

    private void handleAuthResponse(NodeConnection connection, String requestId, JsonNode payload) {
        String nodeId = text(payload, "nodeId");
        String keyId = text(payload, "keyId");
        String challengeId = text(payload, "challengeId");
        String signature = text(payload, "signature");
        if (nodeId == null || keyId == null || challengeId == null || signature == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "认证响应字段缺失");
        }
        NodeAuthResult result = nodeIdentityService.authenticate(
                connection.session.getId(), nodeId, keyId, challengeId, requestId, signature);
        connection.authenticated = true;
        connection.nodeId = nodeId;
        connection.keyId = keyId;
        connection.sessionEpoch = result.getSessionEpoch();
        connection.accessVersion = result.getAccessVersion();
        connection.accessExpiresAt = result.getAccessExpiresAt();
        connection.maxConcurrency = result.getMaxConcurrency() == null || result.getMaxConcurrency() <= 0
                ? DEFAULT_AVAILABLE_SLOTS : result.getMaxConcurrency();
        connection.weight = result.getWeight() == null || result.getWeight() <= 0
                ? DEFAULT_WEIGHT : result.getWeight();
        // 认证时同步 DB 权威排空状态：重连到排空节点的连接不得继续接收调度。
        boolean authoritativeDraining = Boolean.TRUE.equals(result.getDraining())
                || NodeProtocolConstants.NODE_STATUS_DRAINING.equals(result.getStatus());
        if (authoritativeDraining) {
            connection.markDraining();
        } else {
            connection.clearDraining();
        }
        connection.lastInboundAt = System.currentTimeMillis();
        publishDirectory(connection);
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("nodeId", result.getNodeId());
        ack.put("keyId", result.getKeyId());
        ack.put("accessToken", result.getAccessToken());
        ack.put("accessExpiresAt", result.getAccessExpiresAt());
        ack.put("sessionEpoch", result.getSessionEpoch());
        ack.put("serverTime", result.getServerTime());
        ack.put("maxConcurrency", result.getMaxConcurrency());
        ack.put("supportedJudgeModes", result.getSupportedJudgeModes());
        ack.put("authorizationUntil", result.getAuthorizationUntil());
        ack.put("heartbeatIntervalMillis", result.getHeartbeatIntervalMillis());
        send(connection, NodeProtocolConstants.WS_AUTH_OK, requestId, ack);
        // AUTH_OK 之后必须继续发送权威 active/draining 状态，顺序固定为
        // AUTH_CHALLENGE -> AUTH_OK -> NODE_STATE，使重连/刷新即可观察排空状态，
        // 不必等待客户端先发 heartbeat/READY；active 会令节点清除远端排空。
        sendNodeState(connection, authoritativeDraining
                ? NodeProtocolConstants.NODE_STATUS_DRAINING
                : NodeProtocolConstants.NODE_STATUS_ACTIVE, authoritativeDraining);
    }

    private void handleAuthRefresh(NodeConnection connection, String requestId) {
        if (!connection.authenticated) {
            throw new BizException(ResultCode.UNAUTHORIZED, "连接尚未认证");
        }
        sendChallenge(connection, requestId, true);
    }

    private void handleAuthenticated(NodeConnection connection, String type, String requestId, JsonNode payload) {
        if (!connection.authenticated) {
            throw new BizException(ResultCode.UNAUTHORIZED, "连接尚未认证");
        }
        if (connection.accessExpiresAt > 0 && connection.accessExpiresAt <= System.currentTimeMillis()) {
            // 过期业务写入一律拒绝，但入站消息不得抢先关闭连接：
            // 与 reaper 共用同一到期处理，节点终态/硬到期仍需先补发取消与状态。
            sendError(connection, requestId, ResultCode.UNAUTHORIZED, "短期令牌已过期", false);
            handleAccessExpired(connection, System.currentTimeMillis());
            return;
        }
        String nodeId = connection.nodeId;
        String keyId = connection.keyId;
        long epoch = connection.sessionEpoch;
        // 业务前置校验只能辅助；真正的所有权/状态/额度判定在数据平面行锁内重复执行。
        JudgeNodeToken node = nodeIdentityService.requireSessionOwner(nodeId, keyId, epoch,
                connection.accessVersion, true);
        // DB 权威排空状态同步到连接：入站消息一旦发现 drain 翻转立即推送 NODE_STATE，
        // 与后台巡检共用同一去重逻辑，避免 HEARTBEAT 抢先更新 bool 而抑制 reaper 通知。
        syncAuthoritativeDraining(connection, Boolean.TRUE.equals(node.getDraining())
                || NodeProtocolConstants.NODE_STATUS_DRAINING.equals(node.getStatus()));
        if (NodeProtocolConstants.WS_HEARTBEAT.equals(type)) {
            nodeIdentityService.touchHeartbeat(nodeId, epoch, parseRuntimeMetrics(payload));
            refreshDirectory(connection);
            send(connection, NodeProtocolConstants.WS_HEARTBEAT_ACK, requestId, Map.of());
        } else if (NodeProtocolConstants.WS_RESUME_TASKS.equals(type)) {
            handleResumeTasks(connection, requestId, payload);
        } else if (NodeProtocolConstants.WS_READY.equals(type)) {
            handleReady(connection, requestId, payload);
        } else if (NodeProtocolConstants.WS_TASK_ACK.equals(type)) {
            // TASK_ACK 仅确认接收，不等价 Stream ACK；已发放的 READY credit 不回收。
            requireAttemptFields(payload);
            // 合法匹配的 ACK 释放“已发送未 ACK”占用，但不补回 credit；
            // 未知/重复 ACK 不命中在途记录，因此不会增加可用额度。
            connection.acknowledgeAttempt(text(payload, "submissionId"), text(payload, "judgeTaskId"),
                    text(payload, "attemptId"));
        } else if (NodeProtocolConstants.WS_TASK_RUNNING.equals(type)) {
            handleTaskEvent(connection, requestId, payload, false);
        } else if (NodeProtocolConstants.WS_TASK_RESULT.equals(type)) {
            handleTaskEvent(connection, requestId, payload, true);
        } else if (NodeProtocolConstants.WS_LEASE_RENEW.equals(type)) {
            handleLeaseRenew(connection, requestId, payload);
        } else if (NodeProtocolConstants.WS_NODE_DRAIN.equals(type)) {
            // 客户端主动排空必须在 DB 行锁内持久化；重连/READY/heartbeat 不能清除。
            judgeNodeLifecycleService.drain(nodeId);
            connection.markDraining();
            send(connection, NodeProtocolConstants.WS_NODE_STATE, requestId,
                    Map.of("status", NodeProtocolConstants.NODE_STATUS_DRAINING, "draining", true));
        } else {
            throw new BizException(ResultCode.BAD_REQUEST, "未知消息类型: " + type);
        }
    }

    private void handleResumeTasks(NodeConnection connection, String requestId, JsonNode payload) {
        JsonNode nodes = payload.get("attempts");
        if (nodes == null || !nodes.isArray()) {
            // CS 客户端必须发送合法数组；非法/缺失不能静默当空表放行。
            throw new BizException(ResultCode.BAD_REQUEST, "RESUME_TASKS attempts 必须为数组");
        }
        if (nodes.size() > MAX_RESUME_ATTEMPTS) {
            throw new BizException(ResultCode.BAD_REQUEST, "RESUME_TASKS attempt 数量超限");
        }
        JudgeTaskResumeRequest request = new JudgeTaskResumeRequest();
        List<JudgeTaskResumeRequest.Attempt> attempts = new ArrayList<>();
        for (JsonNode node : nodes) {
            if (!node.isObject()) {
                throw new BizException(ResultCode.BAD_REQUEST, "RESUME_TASKS attempt 必须为对象");
            }
            JudgeTaskResumeRequest.Attempt attempt = new JudgeTaskResumeRequest.Attempt();
            attempt.setSubmissionId(text(node, "submissionId"));
            attempt.setJudgeTaskId(text(node, "judgeTaskId"));
            attempt.setAttemptId(text(node, "attemptId"));
            attempts.add(attempt);
        }
        request.setAttempts(attempts);
        JudgeTaskResumeVo result = judgeTaskClaimService.resume(request, caller(connection));
        connection.resumeCompleted = true;

        List<Map<String, Object>> resumed = new ArrayList<>();
        for (JudgeTaskResumeVo.Resumed item : result.getResumed()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("submissionId", item.getSubmissionId());
            value.put("judgeTaskId", item.getJudgeTaskId());
            value.put("attemptId", item.getAttemptId());
            value.put("leaseUntil", item.getLeaseUntil());
            value.put("renewAfterMillis", item.getRenewAfterMillis());
            value.put("completed", item.isCompleted());
            resumed.add(value);
            // 记录 resume 的在途 attempt，供 disable/expiry 精确 TASK_CANCEL。
            connection.addAttempt(item.getSubmissionId(), item.getJudgeTaskId(), item.getAttemptId());
        }
        List<Map<String, Object>> rejected = new ArrayList<>();
        for (JudgeTaskResumeVo.Rejected item : result.getRejected()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("submissionId", item.getSubmissionId());
            value.put("judgeTaskId", item.getJudgeTaskId());
            value.put("attemptId", item.getAttemptId());
            value.put("reason", item.getReason());
            rejected.add(value);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("resumed", resumed);
        response.put("rejected", rejected);
        send(connection, NodeProtocolConstants.WS_RESUME_RESULT, requestId, response);
    }

    private void handleReady(NodeConnection connection, String requestId, JsonNode payload) {
        // RESUME 成功之前禁止 READY/调度
        if (!connection.resumeCompleted) {
            throw new BizException(ResultCode.FORBIDDEN, "READY 前必须完成 RESUME_TASKS");
        }
        if (connection.draining) {
            // 权威排空优先：READY 不能解除排空，也不能重新获得调度 credit。
            connection.clearReady();
            send(connection, NodeProtocolConstants.WS_NODE_STATE, requestId,
                    Map.of("status", NodeProtocolConstants.NODE_STATUS_DRAINING, "draining", true));
            return;
        }
        int slots = payload.path("availableSlots").asInt(DEFAULT_AVAILABLE_SLOTS);
        // 消耗型提示：READY 授予本连接可调度 credit，每次 TASK_ASSIGN 扣减，ACK 不回收。
        connection.applyReady(slots);
    }

    private void handleTaskEvent(NodeConnection connection, String requestId, JsonNode payload, boolean terminalFrame) {
        String submissionId = text(payload, "submissionId");
        JsonNode eventNode = payload.path("event");
        if (submissionId == null || eventNode.isMissingNode() || eventNode.isNull()) {
            throw new BizException(ResultCode.BAD_REQUEST, "task 事件缺少 submissionId/event");
        }
        String eventType = text(eventNode, "eventType");
        // 帧类型与事件类型必须一致：TASK_RESULT 仅接受终态事件，TASK_RUNNING 仅接受进度事件，
        // 不能把 STATUS_CHANGED 回成 TASK_RESULT_ACK。
        if (!acceptedByFrame(terminalFrame, eventType)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    terminalFrame ? "TASK_RESULT 仅接受终态事件" : "TASK_RUNNING 仅接受进度事件");
        }
        JudgeResultEventRequest event;
        try {
            event = objectMapper.treeToValue(eventNode, JudgeResultEventRequest.class);
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "task 事件格式非法");
        }
        event.setSubmissionId(submissionId);
        judgeResultReportService.handleEvent(submissionId, event, caller(connection));
        if (terminalFrame) {
            connection.removeAttempt(submissionId, event.getJudgeTaskId(), event.getAttemptId());
        }
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("submissionId", submissionId);
        ack.put("judgeTaskId", event.getJudgeTaskId());
        ack.put("attemptId", event.getAttemptId());
        send(connection, terminalFrame ? NodeProtocolConstants.WS_TASK_RESULT_ACK
                : NodeProtocolConstants.WS_TASK_EVENT_ACK, requestId, ack);
    }

    private void handleLeaseRenew(NodeConnection connection, String requestId, JsonNode payload) {
        String submissionId = text(payload, "submissionId");
        String judgeTaskId = text(payload, "judgeTaskId");
        String attemptId = text(payload, "attemptId");
        if (submissionId == null || judgeTaskId == null || attemptId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "LEASE_RENEW 缺少 submissionId/judgeTaskId/attemptId");
        }
        JudgeTaskLeaseRequest leaseRequest = new JudgeTaskLeaseRequest();
        leaseRequest.setJudgeTaskId(judgeTaskId);
        leaseRequest.setAttemptId(attemptId);
        JudgeTaskLeaseVo renewed = judgeTaskClaimService.renew(submissionId, caller(connection), leaseRequest);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("submissionId", submissionId);
        response.put("judgeTaskId", judgeTaskId);
        response.put("attemptId", attemptId);
        response.put("leaseUntil", renewed.getLeaseUntil());
        send(connection, NodeProtocolConstants.WS_LEASE_RENEWED, requestId, response);
    }

    /**
     * 进度/终态帧与事件类型匹配判定：TASK_RESULT 仅终态事件，TASK_RUNNING 仅进度事件。
     *
     * @param terminalFrame 是否为 TASK_RESULT 帧
     * @param eventType     事件类型
     * @return 是否允许
     */
    static boolean acceptedByFrame(boolean terminalFrame, String eventType) {
        if (eventType == null) {
            return false;
        }
        return terminalFrame ? TERMINAL_EVENT_TYPES.contains(eventType)
                : PROGRESS_EVENT_TYPES.contains(eventType);
    }

    private void requireAttemptFields(JsonNode payload) {
        if (text(payload, "submissionId") == null || text(payload, "judgeTaskId") == null
                || text(payload, "attemptId") == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "TASK_ACK 缺少 submissionId/judgeTaskId/attemptId");
        }
    }

    /**
     * 解析 HEARTBEAT 运行指标并做有界校验；非法值直接拒绝，不落库。
     */
    private NodeRuntimeMetrics parseRuntimeMetrics(JsonNode payload) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return null;
        }
        NodeRuntimeMetrics metrics = new NodeRuntimeMetrics();
        metrics.setCpuCore(intOrNull(payload, "cpuCore"));
        metrics.setRunningTasks(longOrNull(payload, "runningTasks"));
        metrics.setCacheProblemCount(intOrNull(payload, "cacheProblemCount"));
        metrics.setCacheUsedBytes(longOrNull(payload, "cacheUsedBytes"));
        metrics.setDiskTotalBytes(longOrNull(payload, "diskTotalBytes"));
        metrics.setDiskFreeBytes(longOrNull(payload, "diskFreeBytes"));
        metrics.setVersion(text(payload, "version"));
        requireRange(metrics.getCpuCore(), MAX_CPU_CORE, "cpuCore");
        requireRange(metrics.getRunningTasks(), MAX_RUNNING_TASKS, "runningTasks");
        requireRange(metrics.getCacheProblemCount(), MAX_CACHE_PROBLEM_COUNT, "cacheProblemCount");
        requireRange(metrics.getCacheUsedBytes(), MAX_STORAGE_BYTES, "cacheUsedBytes");
        requireRange(metrics.getDiskTotalBytes(), MAX_STORAGE_BYTES, "diskTotalBytes");
        requireRange(metrics.getDiskFreeBytes(), MAX_STORAGE_BYTES, "diskFreeBytes");
        if (diskFreeExceedsTotal(metrics)) {
            throw new BizException(ResultCode.BAD_REQUEST, "diskFreeBytes 不能大于 diskTotalBytes");
        }
        if (metrics.getVersion() != null && metrics.getVersion().length() > MAX_VERSION_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "version 过长");
        }
        return metrics;
    }

    private boolean diskFreeExceedsTotal(NodeRuntimeMetrics metrics) {
        if (metrics.getDiskTotalBytes() == null || metrics.getDiskFreeBytes() == null) {
            return false;
        }
        return metrics.getDiskFreeBytes() > metrics.getDiskTotalBytes();
    }

    private void requireRange(Integer value, int max, String fieldName) {
        if (value == null) {
            return;
        }
        if (value < 0 || value > max) {
            throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 不合法");
        }
    }

    private void requireRange(Long value, long max, String fieldName) {
        if (value == null) {
            return;
        }
        if (value < 0 || value > max) {
            throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 不合法");
        }
    }

    private SignedCaller caller(NodeConnection connection) {
        return new SignedCaller(connection.nodeId, connection.keyId,
                connection.accessVersion, connection.sessionEpoch);
    }

    private void sendChallenge(NodeConnection connection, String requestId, boolean refresh) {
        NodeAuthChallengeVo challenge = nodeIdentityService.createAuthChallenge(
                connection.session.getId(),
                refresh ? connection.nodeId : null,
                refresh ? connection.sessionEpoch : null,
                requestId, refresh);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("challengeId", challenge.getChallengeId());
        payload.put("nonce", challenge.getNonce());
        payload.put("audience", challenge.getAudience());
        payload.put("expiresAt", challenge.getExpiresAt());
        payload.put("serverTime", challenge.getServerTime());
        send(connection, NodeProtocolConstants.WS_AUTH_CHALLENGE, requestId, payload);
    }

    /**
     * 向指定 READY 连接下发 TASK_ASSIGN 并返回是否真正消费 credit 后发出。
     * DB 已在 claim 事务内提交租约，发送失败由恢复流程回收；
     * 下发即消耗该连接一个 READY credit，并把 attempt 记入在途集合供精确 cancel。
     */
    boolean sendTaskAssign(NodeConnection connection, JudgeTaskClaimVo claim) {
        if (connection == null || connection.closed) {
            return false;
        }
        String submissionId = claim.getTask() == null ? null : claim.getTask().getSubmissionId();
        String judgeTaskId = claim.getTask() == null ? null : claim.getTask().getJudgeTaskId();
        // 原子占用：扣减一个 READY credit 并登记“已发送未 ACK”，发送与 READY 更新在同一把锁内同步。
        if (!connection.beginAssignment(submissionId, judgeTaskId, claim.getAttemptId())) {
            // credit 已被并发消费/排空：不再下发，DB 租约由恢复流程回收。
            return false;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task", claim.getTask());
        payload.put("attemptId", claim.getAttemptId());
        payload.put("leaseUntil", claim.getLeaseUntil());
        payload.put("renewAfterMillis", claim.getRenewAfterMillis());
        send(connection, NodeProtocolConstants.WS_TASK_ASSIGN, UUID.randomUUID().toString(), payload);
        return true;
    }

    /**
     * 服务器主动取消任务（revoke/disable/hard expiry），payload 采用 tasks 数组。
     */
    void sendTaskCancel(NodeConnection connection, List<Map<String, Object>> tasks, String reason) {
        if (connection == null || connection.closed) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tasks", tasks);
        payload.put("reason", reason);
        send(connection, NodeProtocolConstants.WS_TASK_CANCEL, UUID.randomUUID().toString(), payload);
    }

    /**
     * 推送权威节点状态；通知只用于加速旧连接关停，权威检查仍在 DB。
     */
    void sendNodeState(NodeConnection connection, String status, boolean draining) {
        if (connection == null || connection.closed) {
            return;
        }
        send(connection, NodeProtocolConstants.WS_NODE_STATE, UUID.randomUUID().toString(),
                Map.of("status", status, "draining", draining));
    }

    /**
     * 权威排空状态收敛：只在连接状态真正翻转时更新本地标记并推送一次 NODE_STATE。
     * 认证/刷新、入站消息（HEARTBEAT/READY/...）与后台巡检共用同一去重通知逻辑，
     * 避免任一入口抢先更新 {@code connection.draining} 后抑制其他入口的通知。
     * drain 推送 DRAINING；enable 推送 ACTIVE，节点据此清除远端排空（本地 SIGTERM 不受影响）。
     */
    private void syncAuthoritativeDraining(NodeConnection connection, boolean authoritativeDraining) {
        if (connection == null || connection.closed) {
            return;
        }
        synchronized (connection) {
            if (authoritativeDraining == connection.draining) {
                return;
            }
            if (authoritativeDraining) {
                connection.markDraining();
            } else {
                connection.clearDraining();
            }
        }
        sendNodeState(connection,
                authoritativeDraining ? NodeProtocolConstants.NODE_STATUS_DRAINING
                        : NodeProtocolConstants.NODE_STATUS_ACTIVE,
                authoritativeDraining);
    }

    List<NodeConnection> readyConnections() {
        List<NodeConnection> ready = new ArrayList<>();
        for (NodeConnection connection : connections.values()) {
            if (connection.isReady()) {
                ready.add(connection);
            }
        }
        return ready;
    }

    NodeConnection connectionOf(String sessionId) {
        return connections.get(sessionId);
    }

    private void send(NodeConnection connection, String type, String requestId, Object payload) {
        String json;
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("version", NodeProtocolConstants.PROTOCOL_VERSION);
            envelope.put("type", type);
            envelope.put("requestId", requestId);
            envelope.put("timestamp", System.currentTimeMillis());
            envelope.put("payload", payload);
            json = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.warn("judge node ws serialize failed, session {}: {}", connection.session.getId(), e.getMessage());
            close(connection);
            return;
        }
        enqueue(connection, () -> writeMessage(connection, json));
    }

    private void sendError(NodeConnection connection, String requestId, int code, String message, boolean retryable) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        payload.put("retryable", retryable);
        send(connection, NodeProtocolConstants.WS_ERROR, requestId, payload);
    }

    private void rejectAndClose(NodeConnection connection, int code, String message) {
        enqueue(connection, () -> writeMessage(connection, errorJson(null, code, message, false)));
        // close 绝不排在已堵塞的 writer 后面：独立硬超时关闭。
        scheduleClose(connection, CANCEL_CLOSE_GRACE_MILLIS);
    }

    private void writeMessage(NodeConnection connection, String json) {
        if (connection.closed) {
            return;
        }
        // 每次 socket 写入都有独立截止：孤立阻塞的 writer（无新写/队列未满/idle 未到/DB 未返回）
        // 也会在截止后由调度线程强制关停，而不依赖后续写入触发 decorator 的 sendTimeLimit。
        WriteDeadline deadline = new WriteDeadline(connection);
        if (!deadline.arm()) {
            // 调度器拒绝（已关闭/资源耗尽）：不能无 deadline 继续阻塞，立即停止本次写入并关停连接。
            log.warn("judge node ws write deadline scheduling rejected, session {}: closing",
                    connection.session.getId());
            close(connection);
            return;
        }
        try {
            connection.session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.warn("judge node ws send failed, session {}: {}", connection.session.getId(), e.getMessage());
            close(connection);
        } finally {
            deadline.complete();
        }
    }

    /**
     * 单次写入的“完成/超时”原子标记：只有把仍处于未完成的标记原子翻转为超时的 deadline Runnable
     * 才有权关停连接；写入正常完成后再迟到的 Runnable 一律放弃，绝不误关后续健康写入。
     * 这也覆盖 {@code cancel(false)} 无法阻止“已开始执行”的 Runnable 的竞态。
     */
    private final class WriteDeadline {

        private final NodeConnection connection;
        private final AtomicBoolean settled = new AtomicBoolean(false);
        private volatile ScheduledFuture<?> future;

        private WriteDeadline(NodeConnection connection) {
            this.connection = connection;
        }

        /**
         * 安排强制关闭截止。
         *
         * @return 调度是否成功；失败时调用方必须停止写入并关闭，不能无截止继续阻塞
         */
        private boolean arm() {
            try {
                future = nodeWsScheduler.schedule(this::expire,
                        WRITER_SEND_TIME_LIMIT_MS, TimeUnit.MILLISECONDS);
                return true;
            } catch (RejectedExecutionException e) {
                return false;
            }
        }

        private void expire() {
            // 本次写入已完成/已被其他 timer 超时：迟到的 Runnable 直接放弃。
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            if (connection.closed) {
                return;
            }
            log.warn("judge node ws write deadline exceeded, session {}: forcing close",
                    connection.session.getId());
            close(connection);
        }

        private void complete() {
            settled.compareAndSet(false, true);
            ScheduledFuture<?> current = future;
            if (current != null) {
                current.cancel(false);
            }
        }
    }

    private String errorJson(String requestId, int code, String message, boolean retryable) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("code", code);
            payload.put("message", message);
            payload.put("retryable", retryable);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("version", NodeProtocolConstants.PROTOCOL_VERSION);
            envelope.put("type", NodeProtocolConstants.WS_ERROR);
            envelope.put("requestId", requestId);
            envelope.put("timestamp", System.currentTimeMillis());
            envelope.put("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            return "{\"version\":1,\"type\":\"ERROR\",\"payload\":{\"code\":500,\"retryable\":false}}";
        }
    }

    private void enqueue(NodeConnection connection, Runnable task) {
        if (connection.closed) {
            return;
        }
        try {
            connection.writer.execute(task);
        } catch (RejectedExecutionException e) {
            // 写队列已满/已关闭：直接关闭连接，绝不阻塞调用线程。
            close(connection);
        }
    }

    /**
     * 独立于 writer 的硬超时关闭：即使 writer 被慢 socket 堵塞，也能按时关停。
     */
    private void scheduleClose(NodeConnection connection, long delayMillis) {
        if (connection == null || connection.closed || connection.closeScheduled) {
            return;
        }
        connection.closeScheduled = true;
        if (delayMillis <= 0) {
            close(connection);
            return;
        }
        try {
            nodeWsScheduler.schedule(() -> close(connection), delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            close(connection);
        }
    }

    private void reapConnections() {
        try {
            long now = System.currentTimeMillis();
            for (NodeConnection connection : connections.values()) {
                if (connection.closed) {
                    continue;
                }
                if (!connection.authenticated) {
                    long deadline = connection.createdAt + nodeSecurityProperties.getAuthDeadlineSeconds() * 1000L;
                    if (now >= deadline) {
                        enqueue(connection, () -> writeMessage(connection,
                                errorJson(null, ResultCode.UNAUTHORIZED, "认证超时", false)));
                        scheduleClose(connection, CANCEL_CLOSE_GRACE_MILLIS);
                    }
                    continue;
                }
                if (now - connection.lastInboundAt
                        > nodeSecurityProperties.getIdleTimeoutSeconds() * 1000L) {
                    close(connection);
                    continue;
                }
                // accessToken 到期：先按 DB 权威状态区分节点终态/硬到期与仅短期令牌过期。
                if (connection.accessExpiresAt > 0 && connection.accessExpiresAt <= now) {
                    handleAccessExpired(connection, now);
                    continue;
                }
                if (now - connection.lastValidityCheckAt < VALIDITY_CHECK_INTERVAL_MILLIS) {
                    continue;
                }
                connection.lastValidityCheckAt = now;
                checkAuthoritativeState(connection, now);
            }
        } catch (Exception e) {
            log.warn("judge node ws reaper failed: {}", e.getMessage());
        }
    }

    /**
     * 短期令牌到期处理：只有当前 DB 节点终态或硬截止已到，才补发精确 TASK_CANCEL 与过期
     * NODE_STATE，再走既有的有界关闭；永久节点仍有效而仅短期令牌过期时只关连接，
     * 不改节点身份、不取消在途任务。DB 不可用时按失败关闭处理，绝不维持无授权连接。
     */
    private void handleAccessExpired(NodeConnection connection, long now) {
        if (connection == null || connection.closed || connection.closeScheduled) {
            return;
        }
        JudgeNodeToken node;
        try {
            node = nodeIdentityService.findNode(connection.nodeId);
        } catch (Exception e) {
            // 令牌已过期，DB 不可用时不能继续维持连接。
            close(connection);
            return;
        }
        if (node == null) {
            terminateAuthoritatively(connection, NodeProtocolConstants.NODE_STATUS_REVOKED, "节点不存在", false, true);
            return;
        }
        boolean serviceable = isNodeServiceable(node);
        if (serviceable && !isNodeHardExpired(node, now)) {
            // 仅短期令牌过期：只关本连接，绝不把节点永久标记为 revoked/expired。
            close(connection);
            return;
        }
        String authoritative = serviceable ? NodeProtocolConstants.NODE_STATUS_EXPIRED : node.getStatus();
        terminateAuthoritatively(connection, authoritative, "节点已失效", true, true);
    }

    /**
     * 有界权威状态复查：跨实例 disable/revoke/到期/接管无需本机事件即可收敛。
     * 状态失效时先推 TASK_CANCEL/NODE_STATE 再独立关闭；epoch/版本/密钥失效只关连接。
     */
    private void checkAuthoritativeState(NodeConnection connection, long now) {
        JudgeNodeToken node;
        try {
            node = nodeIdentityService.findNode(connection.nodeId);
        } catch (Exception e) {
            // DB 瞬时故障不误关闭连接。
            return;
        }
        if (node == null) {
            terminateAuthoritatively(connection, NodeProtocolConstants.NODE_STATUS_REVOKED, "节点不存在", false, true);
            return;
        }
        // 管理端权重变更无需重连：巡检时刷新连接的 DB 权重快照，供有界公平调度使用。
        if (node.getWeight() != null && node.getWeight() > 0) {
            connection.weight = node.getWeight();
        }
        String status = node.getStatus();
        boolean serviceable = isNodeServiceable(node);
        if (!serviceable || isNodeHardExpired(node, now)) {
            String authoritative = serviceable ? NodeProtocolConstants.NODE_STATUS_EXPIRED : status;
            terminateAuthoritatively(connection, authoritative, "节点已失效", true, true);
            return;
        }
        // 跨实例 admin drain/enable 通过有界 DB 巡检收敛：drain 时停止新调度
        //（保留在途续租/结果），enable 时恢复 active 并通知节点清除远端排空；
        // 与入站消息共用同一去重通知逻辑，不依赖客户端先发 heartbeat/READY。
        boolean authoritativeDraining = Boolean.TRUE.equals(node.getDraining())
                || NodeProtocolConstants.NODE_STATUS_DRAINING.equals(status);
        syncAuthoritativeDraining(connection, authoritativeDraining);
        long currentEpoch = node.getSessionEpoch() == null ? 0L : node.getSessionEpoch();
        int nodeVersion = node.getAccessVersion() == null ? 0 : node.getAccessVersion();
        boolean epochMismatch = currentEpoch != connection.sessionEpoch;
        boolean versionMismatch = connection.accessVersion != null && nodeVersion != connection.accessVersion;
        boolean keyUsable = true;
        try {
            nodeIdentityService.requireUsableKey(connection.nodeId, connection.keyId, true);
        } catch (BizException e) {
            keyUsable = false;
        }
        if (epochMismatch || versionMismatch || !keyUsable) {
            // 令牌/密钥/纪元失效：仅关闭旧连接；节点本身仍可服务，绝不写 revoked。
            terminateAuthoritatively(connection, status, "会话已失效", false, false);
            return;
        }
        refreshDirectory(connection);
    }

    private void terminateAuthoritatively(NodeConnection connection, String status, String reason,
                                          boolean includeCancel, boolean includeNodeState) {
        if (connection == null || connection.closed || connection.closeScheduled) {
            return;
        }
        connection.markDraining();
        if (includeCancel) {
            List<Map<String, Object>> tasks = connection.snapshotAttempts();
            if (!tasks.isEmpty()) {
                sendTaskCancel(connection, tasks, reason);
            }
            // 取消后清理匹配状态，避免同一批 attempt 再次被取消或占用 READY credit。
            connection.clearAttempts();
        }
        if (includeNodeState) {
            sendNodeState(connection, status, true);
        }
        scheduleClose(connection, CANCEL_CLOSE_GRACE_MILLIS);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        close(connections.remove(session.getId()));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("judge node ws transport error, session {}: {}", session.getId(), exception.getMessage());
        close(connections.remove(session.getId()));
    }

    private void close(NodeConnection connection) {
        if (connection == null) {
            return;
        }
        synchronized (connection) {
            if (connection.closed) {
                return;
            }
            connection.closed = true;
            connection.ready = false;
            connection.availableSlots = 0;
        }
        activeConnections.decrementAndGet();
        connections.remove(connection.session.getId());
        // 先打断 writer 并关闭 socket，绝不等 Redis 目录网络调用返回后才释放本地资源。
        connection.writer.shutdownNow();
        if (connection.session.isOpen()) {
            try {
                connection.session.close();
            } catch (Exception ignored) {
                // 关闭失败无需处理
            }
        }
        removeDirectory(connection);
    }

    private void publishDirectory(NodeConnection connection) {
        if (connection.nodeId == null) {
            return;
        }
        String value = directoryValue(connection);
        connection.directoryValue = value;
        try {
            stringRedisTemplate.opsForValue().set(NodeProtocolConstants.REDIS_NODE_SESSION_PREFIX + connection.nodeId,
                    value, Duration.ofSeconds(directoryTtlSeconds()));
        } catch (Exception e) {
            // 目录仅用于加速旧连接关停/跨实例通知，DB 仍是权威；写失败不阻断认证。
            log.warn("judge node directory publish failed, nodeId: {}", connection.nodeId);
        }
    }

    /**
     * compare-refresh：仅当目录仍是本连接写入的值时才续期，避免旧连接复写/续租新 owner。
     */
    private void refreshDirectory(NodeConnection connection) {
        if (connection.nodeId == null || connection.directoryValue == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(COMPARE_REFRESH_SCRIPT,
                    List.of(NodeProtocolConstants.REDIS_NODE_SESSION_PREFIX + connection.nodeId),
                    connection.directoryValue, String.valueOf(directoryTtlSeconds()));
        } catch (Exception e) {
            log.warn("judge node directory compare-refresh failed, nodeId: {}", connection.nodeId);
        }
    }

    private void removeDirectory(NodeConnection connection) {
        if (connection.nodeId == null || connection.directoryValue == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(COMPARE_DELETE_SCRIPT,
                    List.of(NodeProtocolConstants.REDIS_NODE_SESSION_PREFIX + connection.nodeId),
                    connection.directoryValue);
        } catch (Exception e) {
            log.warn("judge node directory compare-delete failed, nodeId: {}", connection.nodeId);
        }
    }

    private String directoryValue(NodeConnection connection) {
        return instanceId + ":" + connection.sessionEpoch + ":" + connection.session.getId();
    }

    private long directoryTtlSeconds() {
        long ttl = nodeSecurityProperties.getIdleTimeoutSeconds() * 2L;
        return ttl <= 0 ? 300L : ttl;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new BizException(ResultCode.BAD_REQUEST, field + " 必须为整数");
        }
        return value.asInt();
    }

    private Long longOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new BizException(ResultCode.BAD_REQUEST, field + " 必须为整数");
        }
        return value.asLong();
    }

    private static long toEpoch(java.time.LocalDateTime time) {
        return time == null ? 0L
                : time.toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli();
    }

    /** 节点是否处于可服务状态（active/draining）。 */
    private static boolean isNodeServiceable(JudgeNodeToken node) {
        String status = node.getStatus();
        return NodeProtocolConstants.NODE_STATUS_ACTIVE.equals(status)
                || NodeProtocolConstants.NODE_STATUS_DRAINING.equals(status);
    }

    /** 节点/授权硬截止是否已到；到期判定与令牌有效期使用同一权威时间语义。 */
    private static boolean isNodeHardExpired(JudgeNodeToken node, long now) {
        return (node.getExpireTime() != null && toEpoch(node.getExpireTime()) <= now)
                || (node.getAuthorizationUntil() != null && toEpoch(node.getAuthorizationUntil()) <= now);
    }

    /**
     * 每连接状态：独立有界串行 writer、认证/READY drain 状态、requestId 防冲突窗口与在途 attempt。
     */
    static final class NodeConnection {
        private final WebSocketSession session;
        private final ThreadPoolExecutor writer;
        private final long createdAt = System.currentTimeMillis();
        private final int rateLimit;
        private final long rateWindowMillis;
        private volatile boolean authenticated;
        private volatile boolean ready;
        private volatile boolean resumeCompleted;
        private volatile boolean closed;
        private volatile boolean closeScheduled;
        private volatile boolean draining;
        private volatile String nodeId;
        private volatile String keyId;
        private volatile long sessionEpoch;
        private volatile Integer accessVersion;
        private volatile long accessExpiresAt;
        private volatile int maxConcurrency = DEFAULT_AVAILABLE_SLOTS;
        private volatile int weight = DEFAULT_WEIGHT;
        private volatile String directoryValue;
        private volatile long lastInboundAt = System.currentTimeMillis();
        private volatile long lastValidityCheckAt;
        private int availableSlots;
        private final AtomicInteger windowCount = new AtomicInteger();
        private volatile long windowStartAt = System.currentTimeMillis();
        private final Map<String, String> requestFingerprints =
                new LinkedHashMap<>(64, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                        return size() > REPLAY_LEDGER_MAX;
                    }
                };
        private final Map<String, Map<String, Object>> activeAttempts =
                new LinkedHashMap<>(64, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Map<String, Object>> eldest) {
                        return size() > ACTIVE_ATTEMPT_MAX;
                    }
                };
        /** 已发送但尚未收到合法 TASK_ACK 的 attempt；READY 授予时据此扣减在途占用。 */
        private final Map<String, Map<String, Object>> pendingAcks =
                new LinkedHashMap<>(64, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Map<String, Object>> eldest) {
                        return size() > ACTIVE_ATTEMPT_MAX;
                    }
                };

        NodeConnection(WebSocketSession session, NodeSecurityProperties properties) {
            this.session = session;
            this.rateLimit = Math.max(1, properties.getMessageRateLimit());
            this.rateWindowMillis = Math.max(MIN_MESSAGE_RATE_WINDOW_SECONDS,
                    properties.getMessageRateWindowSeconds()) * 1000L;
            int queueCapacity = Math.max(1, properties.getWriterQueueCapacity());
            this.writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(queueCapacity),
                    new CustomizableThreadFactory("judge-node-writer-"),
                    new ThreadPoolExecutor.AbortPolicy());
        }

        synchronized boolean isReady() {
            return !closed && authenticated && ready && !draining && availableSlots > 0;
        }

        synchronized int availableSlots() {
            return availableSlots;
        }

        int weight() {
            return weight;
        }

        /**
         * 消耗型 READY：每次 READY 重新授予 credit（不超过 DB 核准额度），
         * 且必须扣除“已发送未 ACK”的在途占用，避免同一批任务被重复计入可用额度。
         * 排空/关闭的连接拒绝授予。
         */
        synchronized boolean applyReady(int slots) {
            if (closed || draining) {
                availableSlots = 0;
                ready = false;
                return false;
            }
            int hint = Math.max(0, Math.min(slots, maxConcurrency));
            availableSlots = Math.max(0, hint - pendingAcks.size());
            ready = availableSlots > 0;
            return ready;
        }

        synchronized void clearReady() {
            availableSlots = 0;
            ready = false;
        }

        synchronized void markDraining() {
            draining = true;
            availableSlots = 0;
            ready = false;
        }

        /** 权威状态恢复 active；仅清除本地排空标记，不动在途 attempt。 */
        synchronized void clearDraining() {
            draining = false;
        }

        /**
         * 下发即原子占用：扣减一个 READY credit，并把 attempt 登记为“已发送未 ACK”。
         * 与 READY 更新共用连接锁，保证发送与额度更新同步；ACK 只释放占用、不补回 credit。
         */
        synchronized boolean beginAssignment(String submissionId, String judgeTaskId, String attemptId) {
            if (availableSlots <= 0) {
                ready = false;
                return false;
            }
            availableSlots--;
            if (availableSlots <= 0) {
                ready = false;
            }
            if (submissionId != null && judgeTaskId != null && attemptId != null) {
                String key = attemptKey(submissionId, judgeTaskId, attemptId);
                Map<String, Object> value = attemptValue(submissionId, judgeTaskId, attemptId);
                activeAttempts.put(key, value);
                pendingAcks.put(key, value);
            }
            return true;
        }

        /**
         * 合法匹配的 TASK_ACK 释放“已发送未 ACK”占用，但不补回 READY credit；
         * 未知/重复 ACK 不命中记录，因此不会增加可用额度。
         *
         * @return 是否命中一个在途未确认 attempt
         */
        synchronized boolean acknowledgeAttempt(String submissionId, String judgeTaskId, String attemptId) {
            if (submissionId == null || judgeTaskId == null || attemptId == null) {
                return false;
            }
            return pendingAcks.remove(attemptKey(submissionId, judgeTaskId, attemptId)) != null;
        }

        synchronized boolean acceptRequest(String requestId, String fingerprint) {
            return acceptLedgerEntry(REQUEST_LEDGER_MESSAGE_PREFIX + requestId, fingerprint);
        }

        /**
         * 认证响应使用独立账本命名空间：合法刷新流程 AUTH_REFRESH(id) -> AUTH_RESPONSE(id)
         * 必须放行；但同一 id 的认证响应若内容变化（挑战/签名不同）仍被拒绝。
         *
         * @param requestId   客户端信封 requestId
         * @param fingerprint 认证响应 type + payload 的摘要
         * @return 是否接受
         */
        synchronized boolean acceptAuthResponse(String requestId, String fingerprint) {
            return acceptLedgerEntry(REQUEST_LEDGER_AUTH_PREFIX + requestId, fingerprint);
        }

        private boolean acceptLedgerEntry(String ledgerKey, String fingerprint) {
            String previous = requestFingerprints.get(ledgerKey);
            if (previous == null) {
                requestFingerprints.put(ledgerKey, fingerprint);
                return true;
            }
            return previous.equals(fingerprint);
        }

        /** RESUME 恢复的是已在途任务，仅登记供精确 cancel，不计入未 ACK 占用。 */
        synchronized void addAttempt(String submissionId, String judgeTaskId, String attemptId) {
            if (submissionId == null || judgeTaskId == null || attemptId == null) {
                return;
            }
            activeAttempts.put(attemptKey(submissionId, judgeTaskId, attemptId),
                    attemptValue(submissionId, judgeTaskId, attemptId));
        }

        /** 终态/取消清理匹配状态：同时移除在途 attempt 与未 ACK 占用。 */
        synchronized void removeAttempt(String submissionId, String judgeTaskId, String attemptId) {
            if (submissionId == null || judgeTaskId == null || attemptId == null) {
                return;
            }
            String key = attemptKey(submissionId, judgeTaskId, attemptId);
            activeAttempts.remove(key);
            pendingAcks.remove(key);
        }

        synchronized List<Map<String, Object>> snapshotAttempts() {
            return new ArrayList<>(activeAttempts.values());
        }

        /** 取消/终态后清理匹配状态：同时释放在途 attempt 与未 ACK 占用。 */
        synchronized void clearAttempts() {
            activeAttempts.clear();
            pendingAcks.clear();
        }

        synchronized int pendingAckCount() {
            return pendingAcks.size();
        }

        private static String attemptKey(String submissionId, String judgeTaskId, String attemptId) {
            return submissionId + ':' + judgeTaskId + ':' + attemptId;
        }

        private static Map<String, Object> attemptValue(String submissionId, String judgeTaskId, String attemptId) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("submissionId", submissionId);
            value.put("judgeTaskId", judgeTaskId);
            value.put("attemptId", attemptId);
            return value;
        }

        private boolean allowInboundMessage() {
            long now = System.currentTimeMillis();
            if (now - windowStartAt >= rateWindowMillis) {
                windowStartAt = now;
                windowCount.set(0);
            }
            return windowCount.incrementAndGet() <= rateLimit;
        }

        WebSocketSession session() {
            return session;
        }

        String nodeId() {
            return nodeId;
        }

        String keyId() {
            return keyId;
        }

        Integer accessVersion() {
            return accessVersion;
        }

        long sessionEpoch() {
            return sessionEpoch;
        }
    }
}
