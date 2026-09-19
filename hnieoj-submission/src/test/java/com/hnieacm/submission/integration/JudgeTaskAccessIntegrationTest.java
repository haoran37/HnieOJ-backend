package com.hnieacm.submission.integration;

import com.hnieacm.common.dto.JudgeTaskAccessRequest;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.judge.NodeProtocolConstants;
import com.hnieacm.common.judge.NodeSignatureCodec;
import com.hnieacm.judge.dto.NodeAuthChallengeVo;
import com.hnieacm.judge.dto.NodeAuthResult;
import com.hnieacm.judge.entity.JudgeNodeKey;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeKeyMapper;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.properties.NodeSecurityProperties;
import com.hnieacm.judge.service.NodeIdentityService;
import com.hnieacm.judge.service.SignedCaller;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.mapper.JudgeTaskExecutionMapper;
import com.hnieacm.submission.service.JudgeTaskAccessService;
import com.hnieacm.submission.service.JudgeTaskClaimService;
import com.hnieacm.submission.service.JudgeTaskMessagePublisher;
import com.hnieacm.submission.vo.JudgeTaskClaimVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实 MySQL/Redis 下的签名测试数据下载资格测试：
 * NODE_ACCESS + Ed25519 原始 HTTP 签名、nonce 单次消费、node/key/epoch 与 tasklease/problem 同事务绑定。
 */
@SpringBootTest(classes = com.hnieacm.submission.SubmissionApplication.class)
@ActiveProfiles("test")
class JudgeTaskAccessIntegrationTest {

    private static final String MYSQL_HOST = System.getProperty("it.mysql.host", "127.0.0.1");
    private static final String MYSQL_PORT = System.getProperty("it.mysql.port", "23306");
    private static final String MYSQL_USER = System.getProperty("it.mysql.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("it.mysql.password", "hnieoj-local-test-only");
    private static final String MYSQL_SCHEMA = System.getProperty("it.mysql.schema", "hnieoj_secure_access_it");
    private static final String REDIS_HOST = System.getProperty("it.redis.host", "127.0.0.1");
    private static final String REDIS_PORT = System.getProperty("it.redis.port", "26379");
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
    }

    @Autowired
    private JudgeTaskAccessService judgeTaskAccessService;
    @Autowired
    private JudgeTaskClaimService claimService;
    @Autowired
    private JudgeTaskMessagePublisher publisher;
    @Autowired
    private NodeIdentityService nodeIdentityService;
    @Autowired
    private NodeSecurityProperties nodeSecurityProperties;
    @Autowired
    private JudgeNodeTokenMapper judgeNodeTokenMapper;
    @Autowired
    private JudgeNodeKeyMapper judgeNodeKeyMapper;
    @Autowired
    private JudgeMapper judgeMapper;
    @Autowired
    private JudgeTaskExecutionMapper executionMapper;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("DELETE FROM judge_task_execution");
        jdbcTemplate.execute("DELETE FROM judge_task_outbox");
        jdbcTemplate.execute("DELETE FROM judge");
        jdbcTemplate.execute("DELETE FROM judge_node_key");
        jdbcTemplate.execute("DELETE FROM judge_node_token");
    }

    @Test
    void signedDownloadRequiresValidSignatureNonceAndLeaseProblemBinding() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String rawPublicKey = rawEd25519PublicKey(keyPair);
        NodeFixture fixture = registerNode(keyPair, rawPublicKey);
        String submissionId = "it-access-sub";
        long problemId = 301L;
        insertAndPublish(submissionId, "it-access-task", problemId);
        JudgeTaskClaimVo claim = claimService.claim(fixture.caller());
        org.assertj.core.api.Assertions.assertThat(claim).isNotNull();

        String method = "GET";
        String path = "/judge/problems/" + problemId + "/testdata?submissionId=" + submissionId
                + "&judgeTaskId=it-access-task&attemptId=" + claim.getAttemptId();
        byte[] body = new byte[0];

        // 1) 合法签名 + 正确绑定
        assertThatCode(() -> judgeTaskAccessService.validateDownloadAccess(
                access(fixture, keyPair, method, path, body, submissionId, "it-access-task",
                        claim.getAttemptId(), problemId, "nonce-ok")))
                .doesNotThrowAnyException();

        // 2) 相同 nonce 重放被拒绝
        assertThatThrownBy(() -> judgeTaskAccessService.validateDownloadAccess(
                access(fixture, keyPair, method, path, body, submissionId, "it-access-task",
                        claim.getAttemptId(), problemId, "nonce-ok")))
                .isInstanceOf(BizException.class);

        // 3) 篡改 problemId：签名仍用原文，但任务绑定不匹配
        assertThatThrownBy(() -> judgeTaskAccessService.validateDownloadAccess(
                access(fixture, keyPair, method, path, body, submissionId, "it-access-task",
                        claim.getAttemptId(), problemId + 1, "nonce-problem")))
                .isInstanceOf(BizException.class);

        // 4) 篡改 body 摘要：签名校验失败
        JudgeTaskAccessRequest tampered = access(fixture, keyPair, method, path, body, submissionId,
                "it-access-task", claim.getAttemptId(), problemId, "nonce-body");
        tampered.setBodySha256(NodeSignatureCodec.sha256Hex("tampered".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> judgeTaskAccessService.validateDownloadAccess(tampered))
                .isInstanceOf(BizException.class);

        // 5) 租约过期后下载资格失效
        jdbcTemplate.update("UPDATE judge_task_execution SET lease_until=? WHERE submission_id=?",
                System.currentTimeMillis() - 1000L, submissionId);
        assertThatThrownBy(() -> judgeTaskAccessService.validateDownloadAccess(
                access(fixture, keyPair, method, path, body, submissionId, "it-access-task",
                        claim.getAttemptId(), problemId, "nonce-expired")))
                .isInstanceOf(BizException.class);
    }

    private JudgeTaskAccessRequest access(NodeFixture fixture, KeyPair keyPair, String method, String path,
                                          byte[] body, String submissionId, String judgeTaskId, String attemptId,
                                          long problemId, String nonce) throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String bodyHash = NodeSignatureCodec.sha256Hex(body);
        String signature = sign(keyPair, NodeSignatureCodec.canonical(NodeProtocolConstants.DOMAIN_HTTP,
                nodeSecurityProperties.getAudience(), fixture.nodeId(), fixture.keyId(), method.toUpperCase(),
                path, bodyHash, timestamp, nonce));
        JudgeTaskAccessRequest request = new JudgeTaskAccessRequest();
        request.setAuthorization("Bearer " + fixture.accessToken());
        request.setNodeId(fixture.nodeId());
        request.setKeyId(fixture.keyId());
        request.setMethod(method);
        request.setPathWithQuery(path);
        request.setBodySha256(bodyHash);
        request.setTimestamp(timestamp);
        request.setNonce(nonce);
        request.setSignature(signature);
        request.setSubmissionId(submissionId);
        request.setJudgeTaskId(judgeTaskId);
        request.setAttemptId(attemptId);
        request.setProblemId(problemId);
        return request;
    }

    private NodeFixture registerNode(KeyPair keyPair, String rawPublicKey) throws Exception {
        String nodeId = UUID.randomUUID().toString().replace("-", "");
        String keyId = UUID.randomUUID().toString().replace("-", "");
        String keyHash = NodeSignatureCodec.sha256Hex(rawPublicKey);
        JudgeNodeToken node = new JudgeNodeToken();
        node.setTokenId(nodeId);
        node.setNodeId(nodeId);
        node.setNodeName("it-access-node");
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

        // 通过真实 AUTH 挑战签发 NODE_ACCESS，保证 token 绑定 key/sessionEpoch。
        String connectionId = "it-access-conn-" + nodeId;
        NodeAuthChallengeVo challenge = nodeIdentityService.createAuthChallenge(
                connectionId, null, null, "req", false);
        String authSignature = sign(keyPair, NodeSignatureCodec.canonical(NodeProtocolConstants.DOMAIN_AUTH,
                challenge.getAudience(), challenge.getChallengeId(), challenge.getNonce(), nodeId, keyId));
        NodeAuthResult auth = nodeIdentityService.authenticate(
                connectionId, nodeId, keyId, challenge.getChallengeId(), "req", authSignature);
        return new NodeFixture(nodeId, keyId, auth.getAccessToken(), auth.getSessionEpoch());
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
            judge.setStatus(-10);
            judgeMapper.insert(judge);
            publisher.publishAfterCommit(judge, null);
            return null;
        });
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

    private record NodeFixture(String nodeId, String keyId, String accessToken, long sessionEpoch) {
        SignedCaller caller() {
            return new SignedCaller(nodeId, keyId, 1, sessionEpoch);
        }
    }
}
