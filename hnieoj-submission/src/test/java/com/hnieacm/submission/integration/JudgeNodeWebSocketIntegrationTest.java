package com.hnieacm.submission.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.judge.NodeProtocolConstants;
import com.hnieacm.common.judge.NodeSignatureCodec;
import com.hnieacm.judge.entity.JudgeNodeKey;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeKeyMapper;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.properties.NodeSecurityProperties;
import com.hnieacm.submission.constant.SubmissionStatusConstant;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.service.JudgeTaskMessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实启动嵌入式容器并收发 WebSocket 帧的节点协议集成测试：
 * 覆盖认证/requestId 绑定、RESUME、READY→TASK_ASSIGN、RUNNING/RESULT 回执与协议拒绝路径。
 */
@SpringBootTest(classes = com.hnieacm.submission.SubmissionApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JudgeNodeWebSocketIntegrationTest {

    private static final String MYSQL_HOST = System.getProperty("it.mysql.host", "127.0.0.1");
    private static final String MYSQL_PORT = System.getProperty("it.mysql.port", "23306");
    private static final String MYSQL_USER = System.getProperty("it.mysql.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("it.mysql.password", "hnieoj-local-test-only");
    private static final String MYSQL_SCHEMA = System.getProperty("it.mysql.schema", "hnieoj_secure_wss_it");
    private static final String REDIS_HOST = System.getProperty("it.redis.host", "127.0.0.1");
    private static final String REDIS_PORT = System.getProperty("it.redis.port", "26379");
    private static final String STREAM_SUFFIX = "wss" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private static volatile boolean schemaReady = false;

    @DynamicPropertySource
    static void localIntegrationProperties(DynamicPropertyRegistry registry) {
        ensureTestSchema();
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/" + MYSQL_SCHEMA
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
        registry.add("spring.datasource.username", () -> MYSQL_USER);
        registry.add("spring.datasource.password", () -> MYSQL_PASSWORD);
        registry.add("spring.data.redis.host", () -> REDIS_HOST);
        registry.add("spring.data.redis.port", () -> Integer.parseInt(REDIS_PORT));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("hnieoj.judge.security.jwt-secret", () -> "integration_judge_jwt_secret_value");
        registry.add("hnieoj.judge.stream.lease-seconds", () -> 10L);
        registry.add("hnieoj.judge.stream.renew-after-millis", () -> 1000L);
        registry.add("hnieoj.judge.stream.execution-deadline-seconds", () -> 120L);
        registry.add("hnieoj.judge.stream.recovery-scan-interval-ms", () -> 3600000L);
        registry.add("hnieoj.submission.judge-outbox.retry-interval-ms", () -> 3600000L);
        registry.add("hnieoj.judge.stream.default-stream-key", () -> "it:wss:default:" + STREAM_SUFFIX);
        registry.add("hnieoj.judge.stream.spj-stream-key", () -> "it:wss:spj:" + STREAM_SUFFIX);
        registry.add("hnieoj.judge.stream.interactive-stream-key", () -> "it:wss:interactive:" + STREAM_SUFFIX);
        registry.add("hnieoj.judge.stream.consumer-group", () -> "it-wss-group-" + STREAM_SUFFIX);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NodeSecurityProperties nodeSecurityProperties;
    @Autowired
    private JudgeNodeTokenMapper judgeNodeTokenMapper;
    @Autowired
    private JudgeNodeKeyMapper judgeNodeKeyMapper;
    @Autowired
    private JudgeMapper judgeMapper;
    @Autowired
    private JudgeTaskMessagePublisher publisher;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("DELETE FROM judge_task_execution");
        jdbcTemplate.execute("DELETE FROM judge_task_outbox");
        jdbcTemplate.execute("DELETE FROM judge");
        jdbcTemplate.execute("DELETE FROM judge_node_key");
        jdbcTemplate.execute("DELETE FROM judge_node_token");
    }

    @Test
    void authenticatesResumesClaimsAndAcksOverRealWebSocket() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String rawPublicKey = rawEd25519PublicKey(keyPair);
        NodeFixture fixture = registerNode(keyPair, rawPublicKey);

        ClientHandler handler = new ClientHandler(objectMapper);
        WebSocketSession session = connect(handler);
        try {
            JsonNode challenge = handler.await("AUTH_CHALLENGE", 5_000L);
            String challengeId = challenge.path("payload").path("challengeId").asText();
            String nonce = challenge.path("payload").path("nonce").asText();
            String challengeRequestId = challenge.path("requestId").asText();

            // READY 在认证前必须被拒绝
            send(session, NodeProtocolConstants.WS_READY, "r-unauth", Map.of("availableSlots", 1));
            JsonNode unauthError = handler.await("ERROR", 5_000L);
            assertThat(unauthError.path("payload").path("code").asInt()).isEqualTo(401);

            String authSignature = sign(keyPair, NodeSignatureCodec.canonical(NodeProtocolConstants.DOMAIN_AUTH,
                    nodeSecurityProperties.getAudience(), challengeId, nonce, fixture.nodeId(), fixture.keyId()));
            Map<String, Object> authPayload = new LinkedHashMap<>();
            authPayload.put("nodeId", fixture.nodeId());
            authPayload.put("keyId", fixture.keyId());
            authPayload.put("challengeId", challengeId);
            authPayload.put("signature", authSignature);
            send(session, NodeProtocolConstants.WS_AUTH_RESPONSE, challengeRequestId, authPayload);
            JsonNode authOk = handler.await("AUTH_OK", 5_000L);
            assertThat(authOk.path("payload").path("sessionEpoch").asLong()).isPositive();

            // RESUME 之前不得 READY/调度
            send(session, NodeProtocolConstants.WS_READY, "r-early", Map.of("availableSlots", 1));
            JsonNode earlyError = handler.await("ERROR", 5_000L);
            assertThat(earlyError.path("payload").path("code").asInt()).isEqualTo(403);

            // 空 RESUME 必须返回真实 RESUME_RESULT，而不是占位
            send(session, NodeProtocolConstants.WS_RESUME_TASKS, "r-resume", Map.of("attempts", java.util.List.of()));
            JsonNode resume = handler.await("RESUME_RESULT", 5_000L);
            assertThat(resume.path("payload").path("resumed").isArray()).isTrue();
            assertThat(resume.path("payload").path("rejected").isArray()).isTrue();

            // 发布任务并 READY，等待真实 TASK_ASSIGN
            insertAndPublish("it-wss-sub", "it-wss-task", 401L);
            send(session, NodeProtocolConstants.WS_READY, "r-ready", Map.of("availableSlots", 1));
            JsonNode assign = handler.await("TASK_ASSIGN", 10_000L);
            String attemptId = assign.path("payload").path("attemptId").asText();
            assertThat(attemptId).isNotBlank();
            assertThat(assign.path("payload").path("task").path("submissionId").asText()).isEqualTo("it-wss-sub");

            // TASK_RUNNING 事务写 + TASK_EVENT_ACK
            send(session, NodeProtocolConstants.WS_TASK_RUNNING, "r-running",
                    taskEvent("it-wss-sub", "it-wss-task", attemptId, SubmissionStatusConstant.RUNNING, null));
            JsonNode eventAck = handler.await("TASK_EVENT_ACK", 5_000L);
            assertThat(eventAck.path("payload").path("attemptId").asText()).isEqualTo(attemptId);

            // TASK_RESULT 终态 commit-before-ACK
            send(session, NodeProtocolConstants.WS_TASK_RESULT, "r-result",
                    taskEvent("it-wss-sub", "it-wss-task", attemptId, SubmissionStatusConstant.ACCEPTED, 100));
            JsonNode resultAck = handler.await("TASK_RESULT_ACK", 5_000L);
            assertThat(resultAck.path("payload").path("submissionId").asText()).isEqualTo("it-wss-sub");

            // 未知帧类型被拒
            send(session, "BOGUS_FRAME", "r-bogus", Map.of());
            JsonNode bogus = handler.await("ERROR", 5_000L);
            assertThat(bogus.path("payload").path("code").asInt()).isEqualTo(400);

            // 过大控制帧被拒
            send(session, NodeProtocolConstants.WS_PING, "r-big", Map.of("pad", "x".repeat(70_000)));
            JsonNode oversized = handler.await("ERROR", 5_000L);
            assertThat(oversized.path("payload").path("message").asText()).contains("控制消息超过大小上限");
        } finally {
            session.close(CloseStatus.NORMAL);
        }
    }

    @Test
    void nodeDrainIsPersistedAndCannotBeClearedByReady() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        NodeFixture fixture = registerNode(keyPair, rawEd25519PublicKey(keyPair));

        ClientHandler handler = new ClientHandler(objectMapper);
        WebSocketSession session = connect(handler);
        try {
            authenticate(session, handler, keyPair, fixture);
            send(session, NodeProtocolConstants.WS_RESUME_TASKS, "d-resume", Map.of("attempts", java.util.List.of()));
            handler.await("RESUME_RESULT", 5_000L);

            send(session, NodeProtocolConstants.WS_NODE_DRAIN, "d-drain", Map.of("draining", true));
            JsonNode state = handler.await("NODE_STATE", 5_000L);
            assertThat(state.path("payload").path("draining").asBoolean()).isTrue();
            // DB 权威持久化：重连/READY 都不能清除。
            Boolean draining = jdbcTemplate.queryForObject(
                    "SELECT draining FROM judge_node_token WHERE token_id = ?", Boolean.class, fixture.nodeId());
            assertThat(draining).isTrue();

            insertAndPublish("it-drain-sub", "it-drain-task", 402L);
            send(session, NodeProtocolConstants.WS_READY, "d-ready", Map.of("availableSlots", 1));
            JsonNode nodeState = handler.await("NODE_STATE", 5_000L);
            assertThat(nodeState.path("payload").path("draining").asBoolean()).isTrue();
            Thread.sleep(1_500L);
            assertThat(handler.countOf("TASK_ASSIGN")).isZero();
        } finally {
            session.close(CloseStatus.NORMAL);
        }
    }

    @Test
    void readyCreditIsConsumedOnceAndAckDoesNotReplenishIt() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        NodeFixture fixture = registerNode(keyPair, rawEd25519PublicKey(keyPair));

        ClientHandler handler = new ClientHandler(objectMapper);
        WebSocketSession session = connect(handler);
        try {
            authenticate(session, handler, keyPair, fixture);
            send(session, NodeProtocolConstants.WS_RESUME_TASKS, "c-resume", Map.of("attempts", java.util.List.of()));
            handler.await("RESUME_RESULT", 5_000L);
            // DB quota 为 2，但一次 READY(1) 只能授予 1 个 credit。
            insertAndPublish("it-credit-1", "it-credit-task-1", 403L);
            insertAndPublish("it-credit-2", "it-credit-task-2", 404L);
            send(session, NodeProtocolConstants.WS_READY, "c-ready", Map.of("availableSlots", 1));
            JsonNode assign = handler.await("TASK_ASSIGN", 10_000L);
            send(session, NodeProtocolConstants.WS_TASK_ACK, "c-ack", Map.of(
                    "submissionId", assign.path("payload").path("task").path("submissionId").asText(),
                    "judgeTaskId", assign.path("payload").path("task").path("judgeTaskId").asText(),
                    "attemptId", assign.path("payload").path("attemptId").asText()));
            Thread.sleep(1_500L);
            assertThat(handler.countOf("TASK_ASSIGN")).isEqualTo(1);
        } finally {
            session.close(CloseStatus.NORMAL);
        }
    }

    @Test
    void taskResultCarryingProgressEventIsRejectedWithoutTerminalAck() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        NodeFixture fixture = registerNode(keyPair, rawEd25519PublicKey(keyPair));

        ClientHandler handler = new ClientHandler(objectMapper);
        WebSocketSession session = connect(handler);
        try {
            authenticate(session, handler, keyPair, fixture);
            send(session, NodeProtocolConstants.WS_RESUME_TASKS, "p-resume", Map.of("attempts", java.util.List.of()));
            handler.await("RESUME_RESULT", 5_000L);
            insertAndPublish("it-progress-sub", "it-progress-task", 405L);
            send(session, NodeProtocolConstants.WS_READY, "p-ready", Map.of("availableSlots", 1));
            JsonNode assign = handler.await("TASK_ASSIGN", 10_000L);
            String attemptId = assign.path("payload").path("attemptId").asText();

            // TASK_RESULT 帧携带 STATUS_CHANGED（进度事件）必须被拒绝，绝不能回 TASK_RESULT_ACK。
            send(session, NodeProtocolConstants.WS_TASK_RESULT, "p-result",
                    taskEvent("it-progress-sub", "it-progress-task", attemptId, SubmissionStatusConstant.RUNNING, null));
            JsonNode error = handler.await("ERROR", 5_000L);
            assertThat(error.path("payload").path("code").asInt()).isEqualTo(400);
            Thread.sleep(500L);
            assertThat(handler.countOf("TASK_RESULT_ACK")).isZero();
        } finally {
            session.close(CloseStatus.NORMAL);
        }
    }

    private void authenticate(WebSocketSession session, ClientHandler handler, KeyPair keyPair, NodeFixture fixture)
            throws Exception {
        JsonNode challenge = handler.await("AUTH_CHALLENGE", 5_000L);
        String challengeId = challenge.path("payload").path("challengeId").asText();
        String nonce = challenge.path("payload").path("nonce").asText();
        String challengeRequestId = challenge.path("requestId").asText();
        String authSignature = sign(keyPair, NodeSignatureCodec.canonical(NodeProtocolConstants.DOMAIN_AUTH,
                nodeSecurityProperties.getAudience(), challengeId, nonce, fixture.nodeId(), fixture.keyId()));
        Map<String, Object> authPayload = new LinkedHashMap<>();
        authPayload.put("nodeId", fixture.nodeId());
        authPayload.put("keyId", fixture.keyId());
        authPayload.put("challengeId", challengeId);
        authPayload.put("signature", authSignature);
        send(session, NodeProtocolConstants.WS_AUTH_RESPONSE, challengeRequestId, authPayload);
        handler.await("AUTH_OK", 5_000L);
    }

    @Test
    void wrongChallengeRequestIdIsRejected() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String rawPublicKey = rawEd25519PublicKey(keyPair);
        NodeFixture fixture = registerNode(keyPair, rawPublicKey);

        ClientHandler handler = new ClientHandler(objectMapper);
        WebSocketSession session = connect(handler);
        try {
            JsonNode challenge = handler.await("AUTH_CHALLENGE", 5_000L);
            String challengeId = challenge.path("payload").path("challengeId").asText();
            String nonce = challenge.path("payload").path("nonce").asText();
            String authSignature = sign(keyPair, NodeSignatureCodec.canonical(NodeProtocolConstants.DOMAIN_AUTH,
                    nodeSecurityProperties.getAudience(), challengeId, nonce, fixture.nodeId(), fixture.keyId()));
            Map<String, Object> authPayload = new LinkedHashMap<>();
            authPayload.put("nodeId", fixture.nodeId());
            authPayload.put("keyId", fixture.keyId());
            authPayload.put("challengeId", challengeId);
            authPayload.put("signature", authSignature);
            // 回传与挑战绑定不一致的 requestId 必须被拒绝
            send(session, NodeProtocolConstants.WS_AUTH_RESPONSE, "wrong-request-id", authPayload);
            JsonNode error = handler.await("ERROR", 5_000L);
            assertThat(error.path("payload").path("code").asInt()).isEqualTo(401);
        } finally {
            session.close(CloseStatus.NORMAL);
        }
    }

    private WebSocketSession connect(ClientHandler handler) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        return client.execute(handler,
                        "ws://127.0.0.1:" + port + NodeProtocolConstants.WS_PATH_JUDGE_NODE)
                .get(10, TimeUnit.SECONDS);
    }

    private void send(WebSocketSession session, String type, String requestId, Object payload) throws Exception {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("version", NodeProtocolConstants.PROTOCOL_VERSION);
        envelope.put("type", type);
        envelope.put("requestId", requestId);
        envelope.put("timestamp", System.currentTimeMillis());
        envelope.put("payload", payload);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
    }

    private Map<String, Object> taskEvent(String submissionId, String judgeTaskId, String attemptId,
                                          int status, Integer score) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", score == null ? "STATUS_CHANGED" : "JUDGE_FINISHED");
        event.put("judgeTaskId", judgeTaskId);
        event.put("attemptId", attemptId);
        event.put("status", status);
        if (score != null) {
            event.put("score", score);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("submissionId", submissionId);
        payload.put("event", event);
        return payload;
    }

    private NodeFixture registerNode(KeyPair keyPair, String rawPublicKey) throws Exception {
        String nodeId = UUID.randomUUID().toString().replace("-", "");
        String keyId = UUID.randomUUID().toString().replace("-", "");
        String keyHash = NodeSignatureCodec.sha256Hex(rawPublicKey);
        JudgeNodeToken node = new JudgeNodeToken();
        node.setTokenId(nodeId);
        node.setNodeId(nodeId);
        node.setNodeName("it-wss-node");
        node.setNodeType(NodeProtocolConstants.NODE_TYPE_FORMAL);
        node.setStatus(NodeProtocolConstants.NODE_STATUS_ACTIVE);
        node.setProofType("ed25519");
        node.setPublicKey(rawPublicKey);
        node.setPublicKeyHash(keyHash);
        node.setExpireTime(LocalDateTime.now().plusDays(1));
        node.setMaxConcurrency(2);
        node.setSupportedJudgeModes("default");
        node.setWeight(10);
        node.setDraining(false);
        node.setRunningTasks(0L);
        node.setSessionEpoch(0L);
        node.setAccessVersion(1);
        node.setActiveKeyId(keyId);
        node.setEnrollmentId(nodeId);
        node.setGmtCreate(LocalDateTime.now());
        node.setGmtModified(LocalDateTime.now());
        judgeNodeTokenMapper.insert(node);

        JudgeNodeKey key = new JudgeNodeKey();
        key.setKeyId(keyId);
        key.setNodeId(nodeId);
        key.setPublicKey(rawPublicKey);
        key.setPublicKeyHash(keyHash);
        key.setStatus(NodeProtocolConstants.KEY_STATUS_ACTIVE);
        key.setActivatedAt(LocalDateTime.now());
        key.setGmtCreate(LocalDateTime.now());
        key.setGmtModified(LocalDateTime.now());
        judgeNodeKeyMapper.insert(key);
        return new NodeFixture(nodeId, keyId);
    }

    private void insertAndPublish(String submissionId, String judgeTaskId, long problemId) {
        new TransactionTemplate(transactionManager).execute(status -> {
            Judge judge = new Judge();
            judge.setSubmitId(submissionId);
            judge.setJudgeTaskId(judgeTaskId);
            judge.setProblemId(problemId);
            judge.setProblemCode("P" + problemId);
            judge.setUid("it-uid");
            judge.setUsername("it-user");
            judge.setLanguage("cpp");
            judge.setCode("int main(){}");
            judge.setStatus(SubmissionStatusConstant.PENDING);
            judgeMapper.insert(judge);
            publisher.publishAfterCommit(judge, null);
            return null;
        });
    }

    private String sign(KeyPair keyPair, byte[] canonical) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(keyPair.getPrivate());
        signature.update(canonical);
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private static String rawEd25519PublicKey(KeyPair keyPair) {
        byte[] encoded = keyPair.getPublic().getEncoded();
        return Base64.getEncoder().encodeToString(Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));
    }

    private record NodeFixture(String nodeId, String keyId) {
    }

    /**
     * 收集服务端帧并支持按 type 等待。
     */
    private static final class ClientHandler extends TextWebSocketHandler {

        private final BlockingQueue<JsonNode> incoming = new LinkedBlockingQueue<>();
        private final java.util.List<JsonNode> received = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final CountDownLatch connected = new CountDownLatch(1);
        private final ObjectMapper mapper;

        private ClientHandler(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            connected.countDown();
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            JsonNode node = mapper.readTree(message.getPayload());
            received.add(node);
            incoming.add(node);
        }

        private long countOf(String type) {
            return received.stream().filter(node -> type.equals(node.path("type").asText())).count();
        }

        private JsonNode await(String type, long timeoutMillis) throws InterruptedException {
            assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (System.currentTimeMillis() < deadline) {
                JsonNode node = incoming.poll(200L, TimeUnit.MILLISECONDS);
                if (node != null && type.equals(node.path("type").asText())) {
                    return node;
                }
            }
            throw new AssertionError("timed out waiting for frame type " + type);
        }
    }

    private static synchronized void ensureTestSchema() {
        if (schemaReady) {
            return;
        }
        String serverUrl = "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT
                + "/?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowMultiQueries=true";
        try (Connection connection = DriverManager.getConnection(serverUrl, MYSQL_USER, MYSQL_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS `" + MYSQL_SCHEMA
                    + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            statement.execute("USE `" + MYSQL_SCHEMA + "`");
            String ddl = new String(new ClassPathResource("sql/judge_it_schema.sql")
                    .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String sql : ddl.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
            schemaReady = true;
        } catch (Exception e) {
            throw new IllegalStateException("Initialize integration test schema failed", e);
        }
    }
}
