package com.hnieacm.submission.integration;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.properties.JudgeStreamProperties;
import com.hnieacm.judge.constant.JudgeNodeConstant;
import com.hnieacm.judge.dto.CreateFormalJudgeTokenRequest;
import com.hnieacm.judge.dto.ExchangeJudgeTempTokenRequest;
import com.hnieacm.judge.dto.JudgeNodeHeartbeatRequest;
import com.hnieacm.judge.entity.JudgeNodeAuthCode;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeAuthCodeMapper;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.service.JudgeNodeHeartbeatService;
import com.hnieacm.judge.service.JudgeNodeSecurityService;
import com.hnieacm.judge.vo.JudgeNodeIdentity;
import com.hnieacm.judge.vo.JudgeTempTokenVo;
import com.hnieacm.submission.constant.JudgeTaskExecutionStatusConstant;
import com.hnieacm.submission.constant.JudgeTaskOutboxStatusConstant;
import com.hnieacm.submission.constant.SubmissionStatusConstant;
import com.hnieacm.submission.dto.JudgeResultEventRequest;
import com.hnieacm.submission.dto.JudgeTaskLeaseRequest;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeTaskExecution;
import com.hnieacm.submission.entity.JudgeTaskOutbox;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.mapper.JudgeTaskExecutionMapper;
import com.hnieacm.submission.mapper.JudgeTaskOutboxMapper;
import com.hnieacm.submission.service.JudgeResultReportService;
import com.hnieacm.submission.service.JudgeTaskClaimService;
import com.hnieacm.submission.service.JudgeTaskMessagePublisher;
import com.hnieacm.submission.service.JudgeTaskStreamService;
import com.hnieacm.submission.service.impl.JudgeTaskRecoveryScanner;
import com.hnieacm.submission.vo.JudgeTaskClaimVo;
import com.hnieacm.submission.vo.JudgeTaskLeaseVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 真实 MySQL8 + Redis7.4 的判题网关集成测试，覆盖领取/续期/结果回传/额度/撤销/恢复。
 * <p>
 * 仅使用本机任务测试容器与测试 schema（hnieoj_judge_it），不触碰生产或预置库，也不连接 Nacos。
 */
@SpringBootTest(classes = com.hnieacm.submission.SubmissionApplication.class)
@ActiveProfiles("test")
class JudgeGatewayIntegrationTest {

    private static final String MYSQL_HOST = System.getProperty("it.mysql.host", "127.0.0.1");
    private static final String MYSQL_PORT = System.getProperty("it.mysql.port", "23306");
    private static final String MYSQL_USER = System.getProperty("it.mysql.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("it.mysql.password", "hnieoj-local-test-only");
    private static final String MYSQL_SCHEMA = System.getProperty("it.mysql.schema", "hnieoj_judge_it");
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
        registry.add("hnieoj.judge.stream.lease-seconds", () -> 2L);
        registry.add("hnieoj.judge.stream.renew-after-millis", () -> 500L);
        registry.add("hnieoj.judge.stream.execution-deadline-seconds", () -> 120L);
        registry.add("hnieoj.judge.stream.max-attempt-count", () -> 3);
        registry.add("hnieoj.judge.stream.stranded-queued-seconds", () -> 1L);
        registry.add("hnieoj.judge.stream.recovery-scan-interval-ms", () -> 3600000L);
    }

    @Autowired
    private JudgeTaskMessagePublisher publisher;
    @Autowired
    private JudgeTaskClaimService claimService;
    @Autowired
    private JudgeResultReportService resultReportService;
    @Autowired
    private JudgeNodeSecurityService securityService;
    @Autowired
    private JudgeNodeHeartbeatService heartbeatService;
    @Autowired
    private JudgeTaskRecoveryScanner recoveryScanner;
    @Autowired
    private JudgeTaskStreamService streamService;
    @Autowired
    private JudgeMapper judgeMapper;
    @Autowired
    private JudgeTaskExecutionMapper executionMapper;
    @Autowired
    private JudgeTaskOutboxMapper outboxMapper;
    @Autowired
    private JudgeNodeTokenMapper judgeNodeTokenMapper;
    @Autowired
    private JudgeNodeAuthCodeMapper judgeNodeAuthCodeMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired
    private JudgeStreamProperties streamProperties;

    @BeforeEach
    void resetState() {
        // 每个用例使用独立 Stream key 与消费组，彻底隔离 PEL/消费位点，避免用例间相互影响
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        streamProperties.setDefaultStreamKey("it:judge:task:default:" + suffix);
        streamProperties.setSpjStreamKey("it:judge:task:spj:" + suffix);
        streamProperties.setInteractiveStreamKey("it:judge:task:interactive:" + suffix);
        streamProperties.setConsumerGroup("it-judge-group-" + suffix);
        jdbcTemplate.execute("DELETE FROM judge_task_execution");
        jdbcTemplate.execute("DELETE FROM judge_task_outbox");
        jdbcTemplate.execute("DELETE FROM judge_case");
        jdbcTemplate.execute("DELETE FROM rejudge_task_detail");
        jdbcTemplate.execute("DELETE FROM judge");
        jdbcTemplate.execute("DELETE FROM judge_node_token");
        jdbcTemplate.execute("DELETE FROM judge_node_auth_code");
        // 只清理本测试专用 key，禁止对可配置的外部 Redis 无条件 FLUSHDB
        java.util.Set<String> testKeys = stringRedisTemplate.keys("it:judge:task:*");
        if (testKeys != null && !testKeys.isEmpty()) {
            stringRedisTemplate.delete(testKeys);
        }
    }

    @Test
    void normalChainClaimRenewProgressFinishAndIdempotentDuplicate() {
        insertAndPublish("it-sub-1", "it-task-1", 101L);
        JudgeNodeIdentity nodeA = formalIdentity(2);

        JudgeTaskClaimVo claim = claimService.claim(nodeA);
        assertThat(claim).isNotNull();
        assertThat(claim.getTask().getSubmissionId()).isEqualTo("it-sub-1");
        assertThat(claim.getAttemptId()).isNotBlank();
        assertThat(claim.getLeaseUntil()).isGreaterThan(System.currentTimeMillis());

        JudgeTaskLeaseVo renewed = claimService.renew("it-sub-1", nodeA,
                leaseRequest("it-task-1", claim.getAttemptId()));
        assertThat(renewed.getLeaseUntil()).isGreaterThanOrEqualTo(claim.getLeaseUntil());

        resultReportService.handleEvent("it-sub-1",
                statusChanged("it-task-1", claim.getAttemptId(), SubmissionStatusConstant.RUNNING, 3, 1, 1), nodeA);
        resultReportService.handleEvent("it-sub-1",
                caseFinished("it-task-1", claim.getAttemptId()), nodeA);
        resultReportService.handleEvent("it-sub-1",
                judgeFinished("it-task-1", claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100), nodeA);

        Judge finished = findJudge("it-sub-1");
        assertThat(finished.getStatus()).isEqualTo(SubmissionStatusConstant.ACCEPTED);
        assertThat(finished.getScore()).isEqualTo(100);
        assertThat(executionOf("it-sub-1").getStatus()).isEqualTo(JudgeTaskExecutionStatusConstant.COMPLETED);

        // 同身份同 attempt 同最终内容重报幂等成功
        resultReportService.handleEvent("it-sub-1",
                judgeFinished("it-task-1", claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100), nodeA);
        assertThat(findJudge("it-sub-1").getStatus()).isEqualTo(SubmissionStatusConstant.ACCEPTED);

        // 冲突终态拒绝
        assertThatThrownBy(() -> resultReportService.handleEvent("it-sub-1",
                judgeFinished("it-task-1", claim.getAttemptId(), SubmissionStatusConstant.WRONG_ANSWER, 0), nodeA))
                .isInstanceOf(BizException.class);
    }

    @Test
    void terminalFinishedWithNonemptyMessageIsIdempotentAndContentChangesRejected() {
        insertAndPublish("it-fin-msg", "it-fin-msg-task", 110L);
        JudgeNodeIdentity node = formalIdentity(1);
        JudgeTaskClaimVo claim = claimService.claim(node);
        assertThat(claim).isNotNull();

        JudgeResultEventRequest first = judgeFinishedMessage("it-fin-msg-task", claim.getAttemptId(),
                SubmissionStatusConstant.ACCEPTED, 100, "accepted final", "diag-1");
        resultReportService.handleEvent("it-fin-msg", first, node);
        assertThat(findJudge("it-fin-msg").getStatus()).isEqualTo(SubmissionStatusConstant.ACCEPTED);
        assertThat(executionOf("it-fin-msg").getTerminalFingerprint()).isNotBlank();

        // 完全相同的非空 message 重报必须幂等成功
        resultReportService.handleEvent("it-fin-msg", judgeFinishedMessage("it-fin-msg-task", claim.getAttemptId(),
                SubmissionStatusConstant.ACCEPTED, 100, "accepted final", "diag-1"), node);
        assertThat(findJudge("it-fin-msg").getStatus()).isEqualTo(SubmissionStatusConstant.ACCEPTED);

        // 省略原始 message 不是通配：必须拒绝
        assertThatThrownBy(() -> resultReportService.handleEvent("it-fin-msg",
                judgeFinished("it-fin-msg-task", claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100), node))
                .isInstanceOf(BizException.class);
        // 修改 message / 分数同样拒绝
        assertThatThrownBy(() -> resultReportService.handleEvent("it-fin-msg",
                judgeFinishedMessage("it-fin-msg-task", claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100,
                        "changed final", "diag-1"), node))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> resultReportService.handleEvent("it-fin-msg",
                judgeFinishedMessage("it-fin-msg-task", claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 20,
                        "accepted final", "diag-1"), node))
                .isInstanceOf(BizException.class);
        // 事件类型不同（终态事件类型参与指纹）拒绝
        assertThatThrownBy(() -> resultReportService.handleEvent("it-fin-msg",
                judgeFailed("it-fin-msg-task", claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100,
                        "accepted final", "diag-1"), node))
                .isInstanceOf(BizException.class);
    }

    @Test
    void terminalFailedWithNonemptyMessageIsIdempotentAndKeepsErrorMessage() {
        insertAndPublish("it-fail-msg", "it-fail-msg-task", 111L);
        JudgeNodeIdentity node = formalIdentity(1);
        JudgeTaskClaimVo claim = claimService.claim(node);
        assertThat(claim).isNotNull();

        resultReportService.handleEvent("it-fail-msg", judgeFailed("it-fail-msg-task", claim.getAttemptId(),
                SubmissionStatusConstant.SYSTEM_ERROR, 0, "boom detail", "diag-fail"), node);
        Judge failed = findJudge("it-fail-msg");
        assertThat(failed.getStatus()).isEqualTo(SubmissionStatusConstant.SYSTEM_ERROR);
        // 保留 JUDGE_FAILED 写入 judge.error_message 的既有业务行为
        assertThat(failed.getErrorMessage()).isEqualTo("boom detail");

        resultReportService.handleEvent("it-fail-msg", judgeFailed("it-fail-msg-task", claim.getAttemptId(),
                SubmissionStatusConstant.SYSTEM_ERROR, 0, "boom detail", "diag-fail"), node);
        assertThat(findJudge("it-fail-msg").getStatus()).isEqualTo(SubmissionStatusConstant.SYSTEM_ERROR);

        assertThatThrownBy(() -> resultReportService.handleEvent("it-fail-msg",
                judgeFailed("it-fail-msg-task", claim.getAttemptId(), SubmissionStatusConstant.SYSTEM_ERROR, 0,
                        "other detail", "diag-fail"), node))
                .isInstanceOf(BizException.class);
    }

    @Test
    void terminalReplayAfterLeaseExpirationSucceedsWithoutRewritingCompletedState() {
        insertAndPublish("it-exp-replay", "it-exp-replay-task", 112L);
        JudgeNodeIdentity node = formalIdentity(1);
        JudgeTaskClaimVo claim = claimService.claim(node);
        assertThat(claim).isNotNull();

        resultReportService.handleEvent("it-exp-replay", judgeFinishedMessage("it-exp-replay-task",
                claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100, "final ok", null), node);
        Judge finished = findJudge("it-exp-replay");
        Integer statusBefore = finished.getStatus();
        Integer scoreBefore = finished.getScore();
        // 租约已过期但执行已终态：同内容重报仍必须幂等成功，不重写已落库业务状态
        expireLease("it-exp-replay");
        resultReportService.handleEvent("it-exp-replay", judgeFinishedMessage("it-exp-replay-task",
                claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100, "final ok", null), node);
        Judge after = findJudge("it-exp-replay");
        assertThat(after.getStatus()).isEqualTo(statusBefore);
        assertThat(after.getScore()).isEqualTo(scoreBefore);
        assertThat(executionOf("it-exp-replay").getStatus()).isEqualTo(JudgeTaskExecutionStatusConstant.COMPLETED);
    }

    @Test
    void literalNullMessageIsDistinctFromOmittedMessageOnReplay() {
        insertAndPublish("it-null-msg", "it-null-msg-task", 115L);
        JudgeNodeIdentity node = formalIdentity(1);
        JudgeTaskClaimVo claim = claimService.claim(node);
        assertThat(claim).isNotNull();

        // 初始终态 message 为字面量 "<null>"；规范化必须与省略 message 的 null 结构上区分
        resultReportService.handleEvent("it-null-msg", judgeFinishedMessage("it-null-msg-task",
                claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100, "<null>", null), node);
        assertThat(executionOf("it-null-msg").getTerminalFingerprint()).isNotBlank();

        // 省略 message（null）不是通配：与字面量 "<null>" 指纹不同，必须拒绝
        assertThatThrownBy(() -> resultReportService.handleEvent("it-null-msg",
                judgeFinished("it-null-msg-task", claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100), node))
                .isInstanceOf(BizException.class);

        // 精确重报字面量 "<null>" 幂等成功
        resultReportService.handleEvent("it-null-msg", judgeFinishedMessage("it-null-msg-task",
                claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100, "<null>", null), node);
        assertThat(findJudge("it-null-msg").getStatus()).isEqualTo(SubmissionStatusConstant.ACCEPTED);
    }

    @Test
    void identicalReplayDoesNotRewritePersistedJudgeOrExecutionState() throws Exception {
        insertAndPublish("it-replay-nowrite", "it-replay-nowrite-task", 116L);
        JudgeNodeIdentity node = formalIdentity(1);
        JudgeTaskClaimVo claim = claimService.claim(node);
        assertThat(claim).isNotNull();

        resultReportService.handleEvent("it-replay-nowrite", judgeFinishedMessage("it-replay-nowrite-task",
                claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100, "final", "diag"), node);

        Map<String, Object> judgeBefore = jdbcTemplate.queryForMap(
                "SELECT status, score, gmt_modified FROM judge WHERE submit_id = ?", "it-replay-nowrite");
        Map<String, Object> executionBefore = jdbcTemplate.queryForMap(
                "SELECT status, attempt_id, lease_until, terminal_fingerprint, gmt_modified "
                        + "FROM judge_task_execution WHERE submission_id = ?", "it-replay-nowrite");

        // 跨过 datetime 秒边界后再重报：若幂等分支仍执行 UPDATE，持久化时间戳与字段会改变
        Thread.sleep(1100L);
        resultReportService.handleEvent("it-replay-nowrite", judgeFinishedMessage("it-replay-nowrite-task",
                claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100, "final", "diag"), node);

        assertThat(jdbcTemplate.queryForMap(
                "SELECT status, score, gmt_modified FROM judge WHERE submit_id = ?", "it-replay-nowrite"))
                .isEqualTo(judgeBefore);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT status, attempt_id, lease_until, terminal_fingerprint, gmt_modified "
                        + "FROM judge_task_execution WHERE submission_id = ?", "it-replay-nowrite"))
                .isEqualTo(executionBefore);
    }

    @Test
    void terminalResultRollbackKeepsFingerprintAndStreamEntryUncommitted() {
        insertAndPublish("it-fp-rb", "it-fp-rb-task", 113L);
        JudgeNodeIdentity node = formalIdentity(1);
        JudgeTaskClaimVo claim = claimService.claim(node);
        assertThat(claim).isNotNull();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            resultReportService.handleEvent("it-fp-rb", judgeFinishedMessage("it-fp-rb-task",
                    claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100, "rolled back", null), node);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        JudgeTaskExecution execution = executionOf("it-fp-rb");
        assertThat(execution.getStatus()).isNotEqualTo(JudgeTaskExecutionStatusConstant.COMPLETED);
        assertThat(execution.getTerminalFingerprint()).isNull();
        assertThat(findJudge("it-fp-rb").getStatus()).isNotEqualTo(SubmissionStatusConstant.ACCEPTED);
        assertThat(stringRedisTemplate.opsForStream().size(streamProperties.getDefaultStreamKey())).isEqualTo(1L);
    }

    @Test
    void recoveryPublishFailurePersistsBackoffAndBoundsRedispatchBudget() {
        judgeTaskIdAndPublish("it-backoff", "it-backoff-task", 114L);
        JudgeNodeIdentity node = formalIdentity(2);
        JudgeTaskClaimVo claim = claimService.claim(node);
        assertThat(claim).isNotNull();
        expireLease("it-backoff");

        CountingFailingStreamService failing = new CountingFailingStreamService();
        Object originalStreamService = ReflectionTestUtils.getField(recoveryScanner, "streamService");
        ReflectionTestUtils.setField(recoveryScanner, "streamService", failing);
        try {
            // Redis 不可用：第一次过期租约恢复必须持久化一次预算与退避
            recoveryScanner.recoverExpiredLeases();
            JudgeTaskOutbox outbox = outboxOf("it-backoff");
            assertThat(failing.publishAttempts.get()).isEqualTo(1);
            assertThat(outbox.getRetryCount()).isEqualTo(1);
            assertThat(outbox.getNextRetryTime()).isNotNull();

            // 未到期的持久化退避：再次扫描不得紧凑重派
            jdbcTemplate.update("UPDATE judge_task_outbox SET next_retry_time = DATE_ADD(NOW(), INTERVAL 1 HOUR) "
                    + "WHERE submission_id = 'it-backoff'");
            ageExecution("it-backoff");
            recoveryScanner.recoverStrandedQueued();
            assertThat(failing.publishAttempts.get()).isEqualTo(1);

            // 到期后只允许消费一次
            jdbcTemplate.update("UPDATE judge_task_outbox SET next_retry_time = DATE_SUB(NOW(), INTERVAL 1 SECOND) "
                    + "WHERE submission_id = 'it-backoff'");
            ageExecution("it-backoff");
            recoveryScanner.recoverStrandedQueued();
            assertThat(failing.publishAttempts.get()).isEqualTo(2);

            // 预算耗尽 -> 终态 SYSTEM_ERROR，且不再重派
            jdbcTemplate.update("UPDATE judge_task_outbox SET retry_count = 2, max_retry_count = 2, "
                    + "next_retry_time = DATE_SUB(NOW(), INTERVAL 1 SECOND) WHERE submission_id = 'it-backoff'");
            ageExecution("it-backoff");
            recoveryScanner.recoverStrandedQueued();
            assertThat(failing.publishAttempts.get()).isEqualTo(2);
            assertThat(executionOf("it-backoff").getStatus()).isEqualTo(JudgeTaskExecutionStatusConstant.FAILED);
            assertThat(findJudge("it-backoff").getStatus()).isEqualTo(SubmissionStatusConstant.SYSTEM_ERROR);
        } finally {
            ReflectionTestUtils.setField(recoveryScanner, "streamService", originalStreamService);
        }
    }

    @Test
    void concurrencyQuotaIsEnforcedAndReleasedWithLease() {
        insertAndPublish("it-sub-2", "it-task-2", 102L);
        insertAndPublish("it-sub-3", "it-task-3", 103L);
        JudgeNodeIdentity node = formalIdentity(1);

        JudgeTaskClaimVo first = claimService.claim(node);
        assertThat(first).isNotNull();
        // 额度已满，第二次领取返回 null 且不消费待分发任务
        assertThat(claimService.claim(node)).isNull();

        expireLease(first.getTask().getSubmissionId());

        JudgeTaskClaimVo second = claimService.claim(node);
        assertThat(second).isNotNull();
        assertThat(second.getTask().getSubmissionId()).isNotEqualTo(first.getTask().getSubmissionId());
    }

    @Test
    void expiredLeaseIsRecoveredAndStaleOwnerCallbackRejected() {
        judgeTaskIdAndPublish("it-sub-4", "it-task-4", 104L);
        JudgeNodeIdentity nodeA = formalIdentity(2);
        JudgeTaskClaimVo claimA = claimService.claim(nodeA);
        assertThat(claimA).isNotNull();

        expireLease("it-sub-4");
        recoveryScanner.recoverExpiredLeases();
        assertThat(executionOf("it-sub-4").getStatus()).isEqualTo(JudgeTaskExecutionStatusConstant.QUEUED);

        JudgeNodeIdentity nodeB = formalIdentity(2);
        JudgeTaskClaimVo claimB = claimService.claim(nodeB);
        assertThat(claimB).isNotNull();
        assertThat(claimB.getAttemptId()).isNotEqualTo(claimA.getAttemptId());

        // A 的旧 attempt 回调必须被拒绝
        assertThatThrownBy(() -> resultReportService.handleEvent("it-sub-4",
                judgeFinished("it-task-4", claimA.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100), nodeA))
                .isInstanceOf(BizException.class);

        resultReportService.handleEvent("it-sub-4",
                judgeFinished("it-task-4", claimB.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100), nodeB);
        assertThat(findJudge("it-sub-4").getStatus()).isEqualTo(SubmissionStatusConstant.ACCEPTED);
    }

    @Test
    void revokedNodeCanNoLongerClaimRenewOrReport() {
        judgeTaskIdAndPublish("it-sub-5", "it-task-5", 105L);
        JudgeNodeIdentity node = formalIdentity(2);
        JudgeTaskClaimVo claim = claimService.claim(node);
        assertThat(claim).isNotNull();

        judgeNodeTokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, node.getTokenId())
                .set(JudgeNodeToken::getStatus, JudgeNodeConstant.TOKEN_REVOKED));

        assertThatThrownBy(() -> securityService.resolveIdentity(null, "Bearer missing"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> claimService.renew("it-sub-5", node,
                leaseRequest("it-task-5", claim.getAttemptId()))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> resultReportService.handleEvent("it-sub-5",
                statusChanged("it-task-5", claim.getAttemptId(), SubmissionStatusConstant.RUNNING, 1, 0, 0), node))
                .isInstanceOf(BizException.class);
    }

    @Test
    void formalRenewKeepsIdentityAndTempRenewCannotExceedAuthorizationUntil() {
        CreateFormalJudgeTokenRequest formalRequest = new CreateFormalJudgeTokenRequest();
        formalRequest.setNodeName("it-formal");
        formalRequest.setMaxConcurrency(2);
        formalRequest.setSupportedJudgeModes(List.of("default"));
        JudgeTempTokenVo formal = securityService.issueFormalToken(formalRequest);
        JudgeTempTokenVo formalRenewed = securityService.renewToken("Bearer " + formal.getToken());
        assertThat(formalRenewed.getNodeId()).isEqualTo(formal.getNodeId());
        assertThat(formalRenewed.getTokenId()).isEqualTo(formal.getTokenId());
        assertThat(formalRenewed.getExpireTime()).isAfterOrEqualTo(formal.getExpireTime());

        String rawCode = "it-auth-code-" + UUID.randomUUID();
        JudgeNodeAuthCode authCode = new JudgeNodeAuthCode();
        authCode.setCodeHash(SecureUtil.sha256(rawCode));
        authCode.setNodeName("it-temp");
        authCode.setMaxExchangeCount(1);
        authCode.setUsedCount(0);
        authCode.setStatus(JudgeNodeConstant.AUTH_CODE_ENABLED);
        authCode.setExpireTime(LocalDateTime.now().plusMinutes(10));
        judgeNodeAuthCodeMapper.insert(authCode);

        ExchangeJudgeTempTokenRequest tempRequest = new ExchangeJudgeTempTokenRequest();
        tempRequest.setAuthCode(rawCode);
        tempRequest.setNodeName("it-temp");
        JudgeTempTokenVo temp = securityService.exchangeTempToken(tempRequest);
        JudgeTempTokenVo tempRenewed = securityService.renewToken("Bearer " + temp.getToken());
        assertThat(tempRenewed.getNodeId()).isEqualTo(temp.getNodeId());
        assertThat(tempRenewed.getTokenId()).isEqualTo(temp.getTokenId());
        assertThat(tempRenewed.getExpireTime()).isBeforeOrEqualTo(temp.getExpireTime());

        // 授权期限被回拨到过去后禁止续期
        judgeNodeTokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, temp.getTokenId())
                .set(JudgeNodeToken::getAuthorizationUntil, LocalDateTime.now().minusMinutes(1)));
        assertThatThrownBy(() -> securityService.renewToken("Bearer " + tempRenewed.getToken()))
                .isInstanceOf(BizException.class);
    }

    @Test
    void duplicatedDeliveryIsDeduplicatedByDatabaseLease() {
        insertAndPublish("it-sub-6", "it-task-6", 106L);
        JudgeTaskOutbox outbox = outboxMapper.selectOne(new LambdaQueryWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getSubmissionId, "it-sub-6").last("limit 1"));
        // 人为重复投递同一条消息
        streamService.publish(outbox.getStreamKey(), outbox.getPayload());

        JudgeNodeIdentity nodeA = formalIdentity(2);
        JudgeNodeIdentity nodeB = formalIdentity(2);
        assertThat(claimService.claim(nodeA)).isNotNull();
        assertThat(claimService.claim(nodeB)).isNull();
    }

    @Test
    void redisLossIsRecoveredFromDatabase() {
        judgeTaskIdAndPublish("it-sub-7", "it-task-7", 107L);
        JudgeTaskExecution execution = executionOf("it-sub-7");
        stringRedisTemplate.delete(execution.getStreamKey());
        ageExecution("it-sub-7");

        recoveryScanner.recoverStrandedQueued();

        JudgeNodeIdentity node = formalIdentity(2);
        JudgeTaskClaimVo claim = claimService.claim(node);
        assertThat(claim).isNotNull();
        assertThat(claim.getTask().getSubmissionId()).isEqualTo("it-sub-7");
    }

    @Test
    void exhaustedAttemptsTerminalSystemError() {
        judgeTaskIdAndPublish("it-sub-8", "it-task-8", 108L);
        JudgeNodeIdentity node = formalIdentity(2);
        JudgeTaskClaimVo claim = claimService.claim(node);
        assertThat(claim).isNotNull();
        executionMapper.update(null, new LambdaUpdateWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getSubmissionId, "it-sub-8")
                .set(JudgeTaskExecution::getMaxAttemptCount, 1));
        expireLease("it-sub-8");

        recoveryScanner.recoverExpiredLeases();

        assertThat(executionOf("it-sub-8").getStatus()).isEqualTo(JudgeTaskExecutionStatusConstant.FAILED);
        assertThat(findJudge("it-sub-8").getStatus()).isEqualTo(SubmissionStatusConstant.SYSTEM_ERROR);
    }

    @Test
    void concurrentClaimsNeverExceedQuota() throws Exception {
        for (int i = 1; i <= 4; i++) {
            insertAndPublish("it-conc-" + i, "it-conc-task-" + i, 200L + i);
        }
        JudgeNodeIdentity node = formalIdentity(2);

        List<JudgeTaskClaimVo> claims = runConcurrently(() -> claimService.claim(node), 4);

        List<String> claimedSubmissions = claims.stream()
                .filter(Objects::nonNull)
                .map(claim -> claim.getTask().getSubmissionId())
                .toList();
        assertThat(claimedSubmissions).doesNotHaveDuplicates();
        assertThat(claimedSubmissions).hasSizeLessThanOrEqualTo(2);
        Long activeLeases = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM judge_task_execution WHERE node_id = ? AND status IN ('leased','running') "
                        + "AND lease_until > ?",
                Long.class, node.getNodeId(), System.currentTimeMillis());
        assertThat(activeLeases).isNotNull().isLessThanOrEqualTo(2L);
    }

    @Test
    void duplicateDeliveryConcurrentNodesProduceSingleOwner() throws Exception {
        insertAndPublish("it-owner", "it-owner-task", 300L);
        JudgeTaskOutbox outbox = outboxMapper.selectOne(new LambdaQueryWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getSubmissionId, "it-owner").last("limit 1"));
        // 人为重复入队，让两个节点各自读到一条
        streamService.publish(outbox.getStreamKey(), outbox.getPayload());

        JudgeNodeIdentity nodeA = formalIdentity(2);
        JudgeNodeIdentity nodeB = formalIdentity(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<JudgeTaskClaimVo> first = pool.submit(() -> {
                start.await();
                return claimService.claim(nodeA);
            });
            Future<JudgeTaskClaimVo> second = pool.submit(() -> {
                start.await();
                return claimService.claim(nodeB);
            });
            start.countDown();
            JudgeTaskClaimVo claimA = first.get(60, TimeUnit.SECONDS);
            JudgeTaskClaimVo claimB = second.get(60, TimeUnit.SECONDS);
            assertThat(Stream.of(claimA, claimB).filter(Objects::nonNull).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        Long owners = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM judge_task_execution WHERE submission_id = 'it-owner' AND status IN ('leased','running')",
                Long.class);
        assertThat(owners).isEqualTo(1L);
    }

    @Test
    void rollbackDoesNotWriteOutboxOrXadd() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            Judge judge = insertJudge("it-rb", "it-rb-task", 400L);
            publisher.publishAfterCommit(judge, null);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        Long outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM judge_task_outbox WHERE submission_id = 'it-rb'", Long.class);
        assertThat(outboxCount).isZero();
        assertThat(stringRedisTemplate.keys("it:judge:task:*")).isEmpty();
    }

    @Test
    void committedPublishIsDurableForIndependentConnection() throws Exception {
        insertAndPublish("it-commit", "it-commit-task", 401L);

        JudgeTaskOutbox outbox = outboxMapper.selectOne(new LambdaQueryWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getSubmissionId, "it-commit").last("limit 1"));
        assertThat(outbox.getStatus()).isEqualTo(JudgeTaskOutboxStatusConstant.SENT);
        assertThat(outbox.getStreamId()).isNotBlank();

        // 独立数据库连接验证提交后 outbox 与 streamId 已持久化
        try (Connection connection = DriverManager.getConnection(mysqlUrl(MYSQL_SCHEMA), MYSQL_USER, MYSQL_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status, stream_id FROM judge_task_outbox WHERE submission_id = ?")) {
            statement.setString(1, "it-commit");
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo(JudgeTaskOutboxStatusConstant.SENT);
                assertThat(resultSet.getString(2)).isNotBlank();
            }
        }
        assertThat(stringRedisTemplate.opsForStream().size(outbox.getStreamKey())).isEqualTo(1L);
    }

    @Test
    void resultRollbackKeepsStreamEntryUnacked() {
        insertAndPublish("it-rb2", "it-rb2-task", 405L);
        JudgeNodeIdentity node = formalIdentity(1);
        JudgeTaskClaimVo claim = claimService.claim(node);
        assertThat(claim).isNotNull();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            resultReportService.handleEvent("it-rb2",
                    judgeFinished("it-rb2-task", claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100), node);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(findJudge("it-rb2").getStatus()).isNotEqualTo(SubmissionStatusConstant.ACCEPTED);
        assertThat(stringRedisTemplate.opsForStream().size(streamProperties.getDefaultStreamKey())).isEqualTo(1L);
    }

    @Test
    void streamEntryRetainedWhileLeasedAndRemovedAfterTerminalAck() {
        insertAndPublish("it-stream", "it-stream-task", 403L);
        JudgeNodeIdentity node = formalIdentity(1);
        JudgeTaskClaimVo claim = claimService.claim(node);
        assertThat(claim).isNotNull();

        String streamKey = streamProperties.getDefaultStreamKey();
        assertThat(stringRedisTemplate.opsForStream().size(streamKey)).isEqualTo(1L);

        resultReportService.handleEvent("it-stream",
                judgeFinished("it-stream-task", claim.getAttemptId(), SubmissionStatusConstant.ACCEPTED, 100), node);

        assertThat(stringRedisTemplate.opsForStream().size(streamKey)).isZero();
        assertThat(executionOf("it-stream").getStatus()).isEqualTo(JudgeTaskExecutionStatusConstant.COMPLETED);
    }

    @Test
    void pendingScanAdvancesPastActiveLeasesAndClearsOrphans() {
        Integer oldBatch = streamProperties.getRecoveryBatchSize();
        Long oldIdle = streamProperties.getOrphanPendingMinIdleMillis();
        try {
            streamProperties.setRecoveryBatchSize(1);
            streamProperties.setOrphanPendingMinIdleMillis(1L);
            insertAndPublish("it-pel-1", "it-pel-task-1", 410L);
            insertAndPublish("it-pel-2", "it-pel-task-2", 411L);
            JudgeNodeIdentity node = formalIdentity(2);
            assertThat(claimService.claim(node)).isNotNull();
            assertThat(claimService.claim(node)).isNotNull();

            String streamKey = streamProperties.getDefaultStreamKey();
            assertThat(stringRedisTemplate.opsForStream().size(streamKey)).isEqualTo(2L);
            // 有效租约不因 PEL idle 被清理；两批扫描探针均推进
            recoveryScanner.cleanOrphanPending();
            recoveryScanner.cleanOrphanPending();
            assertThat(stringRedisTemplate.opsForStream().size(streamKey)).isEqualTo(2L);

            expireLease("it-pel-1");
            expireLease("it-pel-2");
            recoveryScanner.cleanOrphanPending();
            recoveryScanner.cleanOrphanPending();
            assertThat(stringRedisTemplate.opsForStream().size(streamKey)).isZero();
        } finally {
            streamProperties.setRecoveryBatchSize(oldBatch);
            streamProperties.setOrphanPendingMinIdleMillis(oldIdle);
        }
    }

    @Test
    void invalidPayloadOutboxIsRetainedForBoundedRetry() {
        JudgeTaskOutbox outbox = new JudgeTaskOutbox();
        outbox.setMessageId("bad-" + UUID.randomUUID());
        outbox.setJudgeTaskId("bad-task");
        outbox.setSubmissionId("it-bad");
        outbox.setExchangeName("redis-streams");
        outbox.setRoutingKey(streamProperties.getDefaultStreamKey());
        outbox.setStreamKey(streamProperties.getDefaultStreamKey());
        outbox.setPayload("{not-json");
        outbox.setStatus(JudgeTaskOutboxStatusConstant.PENDING);
        outbox.setRetryCount(0);
        outbox.setPublishAttempt(0);
        outbox.setMaxRetryCount(10);
        outbox.setNextRetryTime(LocalDateTime.now().withNano(0).minusSeconds(1));
        outboxMapper.insert(outbox);

        publisher.retryOutbox(outbox.getId());

        JudgeTaskOutbox after = outboxMapper.selectById(outbox.getId());
        assertThat(after.getStatus()).isEqualTo(JudgeTaskOutboxStatusConstant.FAILED);
        assertThat(after.getRetryCount()).isEqualTo(1);
        assertThat(after.getNextRetryTime()).isAfter(LocalDateTime.now());
        assertThat(stringRedisTemplate.opsForStream().size(streamProperties.getDefaultStreamKey())).isZero();
    }

    @Test
    void heartbeatCannotEscalateServerAuthorization() {
        CreateFormalJudgeTokenRequest request = new CreateFormalJudgeTokenRequest();
        request.setNodeName("it-hb");
        request.setMaxConcurrency(1);
        request.setSupportedJudgeModes(List.of("default"));
        JudgeTempTokenVo issued = securityService.issueFormalToken(request);

        JudgeNodeHeartbeatRequest heartbeat = new JudgeNodeHeartbeatRequest();
        heartbeat.setMaxConcurrency(999);
        heartbeat.setSupportedJudgeModes(List.of("spj"));
        heartbeatService.recordHeartbeat(null, "Bearer " + issued.getToken(), heartbeat);

        JudgeNodeIdentity identity = securityService.resolveIdentity(null, "Bearer " + issued.getToken());
        assertThat(identity.getMaxConcurrency()).isEqualTo(1);
        assertThat(identity.getSupportedJudgeModes()).containsExactly("default");

        JudgeNodeHeartbeatRequest unsupported = new JudgeNodeHeartbeatRequest();
        unsupported.setSupportedJudgeModes(List.of("go"));
        assertThatThrownBy(() -> heartbeatService.recordHeartbeat(null, "Bearer " + issued.getToken(), unsupported))
                .isInstanceOf(BizException.class);
    }

    @Test
    void adminDrainingStopsClaimAndOrdinaryHeartbeatCannotClearIt() {
        insertAndPublish("it-drain", "it-drain-task", 404L);
        CreateFormalJudgeTokenRequest request = new CreateFormalJudgeTokenRequest();
        request.setNodeName("it-drain-node");
        request.setMaxConcurrency(1);
        request.setSupportedJudgeModes(List.of("default"));
        JudgeTempTokenVo issued = securityService.issueFormalToken(request);
        String bearer = "Bearer " + issued.getToken();

        securityService.updateDraining(issued.getTokenId(), true);
        assertThat(securityService.resolveIdentity(null, bearer).getDraining()).isTrue();
        assertThat(claimService.claim(securityService.resolveIdentity(null, bearer))).isNull();

        JudgeNodeHeartbeatRequest heartbeat = new JudgeNodeHeartbeatRequest();
        heartbeat.setDraining(false);
        heartbeatService.recordHeartbeat(null, bearer, heartbeat);
        assertThat(securityService.resolveIdentity(null, bearer).getDraining()).isTrue();

        securityService.updateDraining(issued.getTokenId(), false);
        assertThat(claimService.claim(securityService.resolveIdentity(null, bearer))).isNotNull();
    }

    @Test
    void expiredTempAuthorizationRejectsRuntimeAccess() {
        String rawCode = "it-expire-code-" + UUID.randomUUID();
        JudgeNodeAuthCode authCode = new JudgeNodeAuthCode();
        authCode.setCodeHash(SecureUtil.sha256(rawCode));
        authCode.setNodeName("it-expire-temp");
        authCode.setMaxExchangeCount(1);
        authCode.setUsedCount(0);
        authCode.setStatus(JudgeNodeConstant.AUTH_CODE_ENABLED);
        authCode.setExpireTime(LocalDateTime.now().plusMinutes(10));
        judgeNodeAuthCodeMapper.insert(authCode);

        ExchangeJudgeTempTokenRequest tempRequest = new ExchangeJudgeTempTokenRequest();
        tempRequest.setAuthCode(rawCode);
        tempRequest.setNodeName("it-expire-temp");
        JudgeTempTokenVo temp = securityService.exchangeTempToken(tempRequest);
        String bearer = "Bearer " + temp.getToken();

        // 服务端签发时固定的临时额度与模式
        JudgeNodeIdentity identity = securityService.resolveIdentity(null, bearer);
        assertThat(identity.getMaxConcurrency()).isEqualTo(1);
        assertThat(identity.getSupportedJudgeModes()).containsExactly("default");

        judgeNodeTokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, temp.getTokenId())
                .set(JudgeNodeToken::getAuthorizationUntil, LocalDateTime.now().minusMinutes(1)));
        assertThatThrownBy(() -> securityService.resolveIdentity(null, bearer)).isInstanceOf(BizException.class);
    }

    private <T> List<T> runConcurrently(Callable<T> action, int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return action.call();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static String mysqlUrl(String schema) {
        return "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/" + schema
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    }

    private void judgeTaskIdAndPublish(String submissionId, String judgeTaskId, Long problemId) {
        insertAndPublish(submissionId, judgeTaskId, problemId);
    }

    /**
     * 模拟真实提交链路：在事务内写入提交/Outbox/执行租约，提交成功后才 XADD。
     */
    private Judge insertAndPublish(String submissionId, String judgeTaskId, Long problemId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Judge judge = insertJudge(submissionId, judgeTaskId, problemId);
            publisher.publishAfterCommit(judge, null);
            return judge;
        });
    }

    private Judge insertJudge(String submissionId, String judgeTaskId, Long problemId) {
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
        return judge;
    }

    private JudgeNodeIdentity formalIdentity(int maxConcurrency) {
        CreateFormalJudgeTokenRequest request = new CreateFormalJudgeTokenRequest();
        request.setNodeName("it-node-" + UUID.randomUUID());
        request.setMaxConcurrency(maxConcurrency);
        request.setSupportedJudgeModes(List.of("default"));
        JudgeTempTokenVo vo = securityService.issueFormalToken(request);
        return securityService.resolveIdentity(null, "Bearer " + vo.getToken());
    }

    private JudgeTaskLeaseRequest leaseRequest(String judgeTaskId, String attemptId) {
        JudgeTaskLeaseRequest request = new JudgeTaskLeaseRequest();
        request.setJudgeTaskId(judgeTaskId);
        request.setAttemptId(attemptId);
        return request;
    }

    private JudgeResultEventRequest statusChanged(String judgeTaskId, String attemptId, int status,
                                                  int total, int judged, int current) {
        JudgeResultEventRequest request = new JudgeResultEventRequest();
        request.setEventType("STATUS_CHANGED");
        request.setSubmissionId(null);
        request.setJudgeTaskId(judgeTaskId);
        request.setAttemptId(attemptId);
        request.setStatus(status);
        request.setTotalCase(total);
        request.setJudgedCase(judged);
        request.setCurrentCase(current);
        return request;
    }

    private JudgeResultEventRequest caseFinished(String judgeTaskId, String attemptId) {
        JudgeResultEventRequest request = new JudgeResultEventRequest();
        request.setEventType("CASE_FINISHED");
        request.setJudgeTaskId(judgeTaskId);
        request.setAttemptId(attemptId);
        request.setStatus(SubmissionStatusConstant.RUNNING);
        JudgeResultEventRequest.CaseResult caseResult = new JudgeResultEventRequest.CaseResult();
        caseResult.setCaseId("1");
        caseResult.setStatus(SubmissionStatusConstant.ACCEPTED);
        caseResult.setTime(12L);
        caseResult.setMemory(2048L);
        caseResult.setScore(100);
        request.setCaseResult(caseResult);
        return request;
    }

    private JudgeResultEventRequest judgeFinished(String judgeTaskId, String attemptId, int status, int score) {
        JudgeResultEventRequest request = new JudgeResultEventRequest();
        request.setEventType("JUDGE_FINISHED");
        request.setJudgeTaskId(judgeTaskId);
        request.setAttemptId(attemptId);
        request.setStatus(status);
        request.setScore(score);
        return request;
    }

    private JudgeResultEventRequest judgeFinishedMessage(String judgeTaskId, String attemptId, int status, int score,
                                                         String message, String diagnosticMessage) {
        JudgeResultEventRequest request = judgeFinished(judgeTaskId, attemptId, status, score);
        request.setMessage(message);
        request.setDiagnosticMessage(diagnosticMessage);
        return request;
    }

    private JudgeResultEventRequest judgeFailed(String judgeTaskId, String attemptId, int status, int score,
                                                String message, String diagnosticMessage) {
        JudgeResultEventRequest request = new JudgeResultEventRequest();
        request.setEventType("JUDGE_FAILED");
        request.setJudgeTaskId(judgeTaskId);
        request.setAttemptId(attemptId);
        request.setStatus(status);
        request.setScore(score);
        request.setMessage(message);
        request.setDiagnosticMessage(diagnosticMessage);
        return request;
    }

    private JudgeTaskOutbox outboxOf(String submissionId) {
        return outboxMapper.selectOne(new LambdaQueryWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getSubmissionId, submissionId).last("limit 1"));
    }

    /**
     * @Author: HaoRan_Lyu
     * @Date 2026/09/18
     * @Description: 测试用 Redis Streams 故障桩：只统计发布次数并强制失败，模拟 Redis 不可用
     */
    static class CountingFailingStreamService implements JudgeTaskStreamService {

        private final java.util.concurrent.atomic.AtomicInteger publishAttempts =
                new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public String resolveStreamKey(String judgeMode) {
            throw new UnsupportedOperationException("test stub");
        }

        @Override
        public String publish(String streamKey, String payloadJson) {
            publishAttempts.incrementAndGet();
            throw new IllegalStateException("forced redis publish failure");
        }

        @Override
        public List<com.hnieacm.submission.dto.JudgeTaskStreamEntry> readNew(String streamKey, String consumerName,
                                                                             int count) {
            throw new UnsupportedOperationException("test stub");
        }

        @Override
        public com.hnieacm.submission.dto.JudgeTaskPendingScan scanPending(String streamKey, String consumerName,
                                                                           long minIdleMillis, int count,
                                                                           String afterId) {
            throw new UnsupportedOperationException("test stub");
        }

        @Override
        public boolean ack(String streamKey, String recordId) {
            throw new UnsupportedOperationException("test stub");
        }
    }

    private Judge findJudge(String submissionId) {
        return judgeMapper.selectOne(new LambdaQueryWrapper<Judge>()
                .eq(Judge::getSubmitId, submissionId).last("limit 1"));
    }

    private JudgeTaskExecution executionOf(String submissionId) {
        return executionMapper.selectOne(new LambdaQueryWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getSubmissionId, submissionId).last("limit 1"));
    }

    private void expireLease(String submissionId) {
        executionMapper.update(null, new LambdaUpdateWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getSubmissionId, submissionId)
                .set(JudgeTaskExecution::getLeaseUntil, System.currentTimeMillis() - 1000L));
    }

    private void ageExecution(String submissionId) {
        executionMapper.update(null, new LambdaUpdateWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getSubmissionId, submissionId)
                .setSql("gmt_modified = DATE_SUB(NOW(), INTERVAL 2 HOUR)"));
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
