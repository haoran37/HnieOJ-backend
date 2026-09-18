package com.hnieacm.submission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.dto.JudgeTaskMessage;
import com.hnieacm.common.properties.JudgeStreamProperties;
import com.hnieacm.submission.constant.JudgeTaskExecutionStatusConstant;
import com.hnieacm.submission.constant.JudgeTaskOutboxStatusConstant;
import com.hnieacm.submission.constant.SubmissionStatusConstant;
import com.hnieacm.submission.dto.JudgeResultEventRequest;
import com.hnieacm.submission.dto.JudgeTaskPendingScan;
import com.hnieacm.submission.dto.JudgeTaskStreamEntry;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeCase;
import com.hnieacm.submission.entity.JudgeTaskExecution;
import com.hnieacm.submission.entity.JudgeTaskOutbox;
import com.hnieacm.submission.entity.RejudgeTaskDetail;
import com.hnieacm.submission.mapper.JudgeCaseMapper;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.mapper.JudgeTaskExecutionMapper;
import com.hnieacm.submission.mapper.JudgeTaskOutboxMapper;
import com.hnieacm.submission.mapper.RejudgeTaskDetailMapper;
import com.hnieacm.submission.service.JudgeTaskStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 判题任务恢复扫描：过期租约/Redis 丢消息补偿、重派预算耗尽终态、PEL 孤儿清理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeTaskRecoveryScanner {

    private static final String EVENT_JUDGE_FAILED = "JUDGE_FAILED";
    private static final String PROGRESS_TOPIC_PREFIX = "/topic/submissions/";
    private static final String PROGRESS_TOPIC_SUFFIX = "/progress";
    private static final String RECOVERY_MESSAGE_LEASE = "Judge task lease expired, retry scheduled";
    private static final String RECOVERY_MESSAGE_EXHAUSTED = "Judge task retry budget exhausted";
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_LEASE_BATCH_SIZE = 50;
    private static final int DEFAULT_MAX_OUTBOX_RETRY_COUNT = 10;
    private static final long DEFAULT_STRANDED_SECONDS = 3600L;
    private static final int MAX_ERROR_LENGTH = 512;

    private final JudgeTaskExecutionMapper executionMapper;
    private final JudgeTaskOutboxMapper outboxMapper;
    private final JudgeMapper judgeMapper;
    private final JudgeCaseMapper judgeCaseMapper;
    private final RejudgeTaskDetailMapper rejudgeTaskDetailMapper;
    private final JudgeTaskStreamService streamService;
    private final JudgeStreamProperties streamProperties;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    /**
     * 每个 Stream 的 PEL 扫描游标：每次只推进一批，避免前面的活跃长任务永久饿死后面的孤儿。
     */
    private final Map<String, String> pendingCursors = new ConcurrentHashMap<>();

    /**
     * @MethodName recoverExpiredLeases
     * @Description 回收过期租约的未完成任务，重派前清除旧测试点/进度/指标
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Scheduled(fixedDelayString = "${hnieoj.judge.stream.recovery-scan-interval-ms:60000}")
    public void recoverExpiredLeases() {
        long now = System.currentTimeMillis();
        List<JudgeTaskExecution> candidates = executionMapper.selectList(new LambdaQueryWrapper<JudgeTaskExecution>()
                .in(JudgeTaskExecution::getStatus,
                        JudgeTaskExecutionStatusConstant.LEASED,
                        JudgeTaskExecutionStatusConstant.RUNNING)
                .lt(JudgeTaskExecution::getLeaseUntil, now)
                .orderByAsc(JudgeTaskExecution::getLeaseUntil)
                .last("limit " + leaseBatchSize()));
        for (JudgeTaskExecution candidate : candidates) {
            recoverBySubmissionId(candidate.getSubmissionId(), true, null);
        }
    }

    /**
     * @MethodName recoverStrandedQueued
     * @Description 补偿 Redis 丢消息/清空后长时间无租约的 queued 任务
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Scheduled(fixedDelayString = "${hnieoj.judge.stream.recovery-scan-interval-ms:60000}", initialDelay = 30000)
    public void recoverStrandedQueued() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(strandedSeconds());
        List<JudgeTaskExecution> candidates = executionMapper.selectList(new LambdaQueryWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getStatus, JudgeTaskExecutionStatusConstant.QUEUED)
                .le(JudgeTaskExecution::getGmtModified, cutoff)
                .orderByAsc(JudgeTaskExecution::getGmtModified)
                .last("limit " + leaseBatchSize()));
        for (JudgeTaskExecution candidate : candidates) {
            recoverBySubmissionId(candidate.getSubmissionId(), false, cutoff);
        }
    }

    /**
     * @MethodName cleanOrphanPending
     * @Description 有界推进地回收空闲过久的 PEL 孤儿：每次每个 Stream 只扫描一批，遇到有效租约跳过，
     * 游标推进保证后面的孤儿最终能被处理，避免重复/过期消息无界增长。
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Scheduled(fixedDelayString = "${hnieoj.judge.stream.recovery-scan-interval-ms:60000}", initialDelay = 45000)
    public void cleanOrphanPending() {
        long minIdle = orphanMinIdleMillis();
        for (String streamKey : List.of(
                streamProperties.getDefaultStreamKey(),
                streamProperties.getSpjStreamKey(),
                streamProperties.getInteractiveStreamKey())) {
            JudgeTaskPendingScan scan;
            try {
                scan = streamService.scanPending(streamKey, "recovery", minIdle, batchSize(),
                        pendingCursors.get(streamKey));
            } catch (Exception e) {
                log.warn("Scan judge stream pending entries failed, streamKey: {}", streamKey, e);
                continue;
            }
            if (scan.nextCursor() == null) {
                pendingCursors.remove(streamKey);
            } else {
                pendingCursors.put(streamKey, scan.nextCursor());
            }
            for (JudgeTaskStreamEntry entry : scan.entries()) {
                if (hasActiveLease(entry.payload())) {
                    // 有效租约的长任务不能仅因 Stream idle 被抢走或提前 ACK
                    continue;
                }
                streamService.ack(entry.streamKey(), entry.recordId());
            }
        }
    }

    private void recoverBySubmissionId(String submissionId, boolean expiredLease, LocalDateTime queuedCutoff) {
        RecoveryPlan plan;
        try {
            plan = new TransactionTemplate(transactionManager)
                    .execute(status -> buildPlan(submissionId, expiredLease, queuedCutoff));
        } catch (Exception e) {
            log.error("Build judge task recovery plan failed, submissionId: {}", submissionId, e);
            return;
        }
        if (plan == null) {
            return;
        }
        if (plan.terminal()) {
            pushTerminalEvent(plan);
            log.warn("Judge task marked terminal SYSTEM_ERROR by recovery, submissionId: {}, judgeTaskId: {}, message: {}",
                    plan.submissionId(), plan.judgeTaskId(), plan.message());
            return;
        }
        String recordId;
        try {
            recordId = streamService.publish(plan.streamKey(), plan.payload());
        } catch (Exception e) {
            log.error("Re-dispatch judge task failed, submissionId: {}, streamKey: {}",
                    plan.submissionId(), plan.streamKey(), e);
            // 退避与预算已在 buildPlan 内预占，这里只记录失败原因，供下一次到期重试与排障
            recordDispatchFailure(plan, e);
            return;
        }
        // 发布后更新携带 judgeTaskId 等 fence，避免 rejudge 后旧恢复计划写入新轮次
        executionMapper.update(null, new LambdaUpdateWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getId, plan.executionId())
                .eq(JudgeTaskExecution::getJudgeTaskId, plan.judgeTaskId())
                .eq(JudgeTaskExecution::getStatus, JudgeTaskExecutionStatusConstant.QUEUED)
                .set(JudgeTaskExecution::getStreamKey, plan.streamKey())
                .set(JudgeTaskExecution::getStreamId, recordId));
        // 补偿预算与退避已在执行行锁内原子预占，成功投递只登记 SENT 并清除到期时间
        outboxMapper.update(null, new LambdaUpdateWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getId, plan.outboxId())
                .eq(JudgeTaskOutbox::getJudgeTaskId, plan.judgeTaskId())
                .set(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.SENT)
                .set(JudgeTaskOutbox::getStreamKey, plan.streamKey())
                .set(JudgeTaskOutbox::getStreamId, recordId)
                .set(JudgeTaskOutbox::getSentTime, LocalDateTime.now())
                .set(JudgeTaskOutbox::getLastError, null)
                .set(JudgeTaskOutbox::getNextRetryTime, null));
        log.info("Judge task re-dispatched by recovery, submissionId: {}, judgeTaskId: {}, streamKey: {}, recordId: {}",
                plan.submissionId(), plan.judgeTaskId(), plan.streamKey(), recordId);
    }

    /**
     * @MethodName compensationDue
     * @Param outbox
     * @Description 判断恢复补偿是否已到持久化的下次重试时间；未到期不产生任何副作用
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    private boolean compensationDue(JudgeTaskOutbox outbox) {
        LocalDateTime nextRetryTime = outbox.getNextRetryTime();
        return nextRetryTime == null || !nextRetryTime.isAfter(LocalDateTime.now());
    }

    /**
     * @MethodName reserveCompensationDispatch
     * @Param outbox
     * @Param expectedRetryCount
     * @Description 在执行行锁事务内原子预占一次补偿投递预算：条件自增 retry_count 并写入退避到期时间，
     * 多扫描器/多后端实例不会重复消费同一次到期尝试
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    private boolean reserveCompensationDispatch(JudgeTaskOutbox outbox, int expectedRetryCount) {
        int updated = outboxMapper.update(null, new LambdaUpdateWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getId, outbox.getId())
                .eq(JudgeTaskOutbox::getRetryCount, expectedRetryCount)
                .setSql("retry_count = retry_count + 1")
                .set(JudgeTaskOutbox::getNextRetryTime,
                        LocalDateTime.now().withNano(0).plusSeconds(redispatchBackoffSeconds())));
        return updated > 0;
    }

    /**
     * @MethodName recordDispatchFailure
     * @Param plan
     * @Param error
     * @Description 记录补偿投递失败原因；不重复递增预算（预算已预占），保证失败可观测且重试有界
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    private void recordDispatchFailure(RecoveryPlan plan, Exception error) {
        if (plan.outboxId() == null) {
            return;
        }
        String message = error == null ? null : error.getMessage();
        if (message != null && message.length() > MAX_ERROR_LENGTH) {
            message = message.substring(0, MAX_ERROR_LENGTH);
        }
        outboxMapper.update(null, new LambdaUpdateWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getId, plan.outboxId())
                .eq(JudgeTaskOutbox::getJudgeTaskId, plan.judgeTaskId())
                .set(JudgeTaskOutbox::getLastError, message));
    }

    private RecoveryPlan buildPlan(String submissionId, boolean expiredLease, LocalDateTime queuedCutoff) {
        JudgeTaskExecution execution = executionMapper.selectBySubmissionIdForUpdate(submissionId);
        if (execution == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (JudgeTaskExecutionStatusConstant.COMPLETED.equals(execution.getStatus())
                || JudgeTaskExecutionStatusConstant.FAILED.equals(execution.getStatus())) {
            return null;
        }
        if (expiredLease) {
            boolean leaseStatus = JudgeTaskExecutionStatusConstant.LEASED.equals(execution.getStatus())
                    || JudgeTaskExecutionStatusConstant.RUNNING.equals(execution.getStatus());
            if (!leaseStatus || (execution.getLeaseUntil() != null && execution.getLeaseUntil() > now)) {
                return null;
            }
        } else {
            if (!JudgeTaskExecutionStatusConstant.QUEUED.equals(execution.getStatus())) {
                return null;
            }
            // 加锁后复核候选 cutoff：并发扫描器不会对同一 queued 任务连续重置/重派
            if (queuedCutoff != null && execution.getGmtModified() != null
                    && execution.getGmtModified().isAfter(queuedCutoff)) {
                return null;
            }
        }

        JudgeTaskOutbox outbox = latestOutbox(execution.getJudgeTaskId());
        int attemptCount = defaultZero(execution.getAttemptCount());
        int maxAttemptCount = execution.getMaxAttemptCount() == null || execution.getMaxAttemptCount() <= 0
                ? defaultMaxAttemptCount() : execution.getMaxAttemptCount();
        boolean deadlineExceeded = execution.getExecutionDeadline() != null && execution.getExecutionDeadline() <= now;

        if (outbox == null) {
            String message = "Judge task cannot be recovered: payload missing";
            markTerminal(execution, message);
            return RecoveryPlan.terminal(execution, message);
        }
        if (deadlineExceeded) {
            String message = "Judge task cannot be recovered: deadline exceeded";
            markTerminal(execution, message);
            return RecoveryPlan.terminal(execution, message);
        }
        // attemptCount 仅由真正领取执行时递增；恢复不消耗执行次数，预算按实际执行次数判断
        if (expiredLease && attemptCount >= maxAttemptCount) {
            markTerminal(execution, RECOVERY_MESSAGE_EXHAUSTED);
            return RecoveryPlan.terminal(execution, RECOVERY_MESSAGE_EXHAUSTED);
        }
        int outboxRetryCount = defaultZero(outbox.getRetryCount());
        int maxOutboxRetryCount = outbox.getMaxRetryCount() == null || outbox.getMaxRetryCount() <= 0
                ? defaultMaxOutboxRetryCount() : outbox.getMaxRetryCount();
        if (outboxRetryCount >= maxOutboxRetryCount) {
            String message = "Judge task dispatch retry budget exhausted";
            markTerminal(execution, message);
            return RecoveryPlan.terminal(execution, message);
        }
        // 持久化退避：补偿重派与 Outbox 共用 next_retry_time/retry_count 预算，未到期直接跳过，
        // 避免 Redis 持续不可用时每个扫描周期紧凑重派
        if (!compensationDue(outbox)) {
            return null;
        }
        // 执行行锁内原子预占一次补偿预算：即使随后 XADD 失败，退避与计数也已持久化，
        // 多扫描器/多实例不会重复消费同一次到期尝试
        if (!reserveCompensationDispatch(outbox, outboxRetryCount)) {
            return null;
        }

        resetAttemptArtifacts(execution);
        executionMapper.update(null, new LambdaUpdateWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getId, execution.getId())
                .eq(JudgeTaskExecution::getJudgeTaskId, execution.getJudgeTaskId())
                .set(JudgeTaskExecution::getStatus, JudgeTaskExecutionStatusConstant.QUEUED)
                .set(JudgeTaskExecution::getNodeId, null)
                .set(JudgeTaskExecution::getTokenId, null)
                .set(JudgeTaskExecution::getAttemptId, null)
                .set(JudgeTaskExecution::getLeaseUntil, null)
                .set(JudgeTaskExecution::getStreamId, null)
                .set(JudgeTaskExecution::getTerminalFingerprint, null)
                .set(JudgeTaskExecution::getLastError, RECOVERY_MESSAGE_LEASE));
        String streamKey = StrUtil.blankToDefault(execution.getStreamKey(), outbox.getStreamKey());
        if (StrUtil.isBlank(streamKey)) {
            streamKey = streamService.resolveStreamKey(resolveJudgeMode(execution, outbox));
        }
        return RecoveryPlan.redispatch(execution.getId(), outbox.getId(), execution.getSubmissionId(),
                execution.getJudgeTaskId(), streamKey, outbox.getPayload());
    }

    private void resetAttemptArtifacts(JudgeTaskExecution execution) {
        judgeCaseMapper.delete(new LambdaQueryWrapper<JudgeCase>()
                .eq(JudgeCase::getSubmitId, execution.getJudgeId()));
        LambdaUpdateWrapper<Judge> wrapper = new LambdaUpdateWrapper<Judge>()
                .eq(Judge::getId, execution.getJudgeId())
                .lt(Judge::getStatus, SubmissionStatusConstant.ACCEPTED)
                .set(Judge::getStatus, SubmissionStatusConstant.PENDING)
                .set(Judge::getTotalCase, 0)
                .set(Judge::getJudgedCase, 0)
                .set(Judge::getCurrentCase, 0)
                .set(Judge::getTime, null)
                .set(Judge::getMemory, null)
                .set(Judge::getScore, null)
                .set(Judge::getErrorMessage, null)
                .set(Judge::getDiagnosticMessage, null);
        if (execution.getJudgeTaskId() != null) {
            wrapper.eq(Judge::getJudgeTaskId, execution.getJudgeTaskId());
        }
        judgeMapper.update(null, wrapper);
    }

    private void markTerminal(JudgeTaskExecution execution, String message) {
        executionMapper.update(null, new LambdaUpdateWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getId, execution.getId())
                .set(JudgeTaskExecution::getStatus, JudgeTaskExecutionStatusConstant.FAILED)
                .set(JudgeTaskExecution::getLeaseUntil, null)
                .set(JudgeTaskExecution::getLastError, message));
        LambdaUpdateWrapper<Judge> judgeWrapper = new LambdaUpdateWrapper<Judge>()
                .eq(Judge::getId, execution.getJudgeId())
                .lt(Judge::getStatus, SubmissionStatusConstant.ACCEPTED)
                .set(Judge::getStatus, SubmissionStatusConstant.SYSTEM_ERROR)
                .set(Judge::getScore, 0)
                .set(Judge::getErrorMessage, message);
        if (execution.getJudgeTaskId() != null) {
            judgeWrapper.eq(Judge::getJudgeTaskId, execution.getJudgeTaskId());
        }
        judgeMapper.update(null, judgeWrapper);
        if (execution.getJudgeTaskId() != null) {
            rejudgeTaskDetailMapper.update(null, new LambdaUpdateWrapper<RejudgeTaskDetail>()
                    .eq(RejudgeTaskDetail::getJudgeId, execution.getJudgeId())
                    .eq(RejudgeTaskDetail::getJudgeTaskId, execution.getJudgeTaskId())
                    .set(RejudgeTaskDetail::getFinalStatus, SubmissionStatusConstant.SYSTEM_ERROR)
                    .set(RejudgeTaskDetail::getFinalScore, 0)
                    .set(RejudgeTaskDetail::getFinishedTime, LocalDateTime.now()));
        }
    }

    private JudgeTaskOutbox latestOutbox(String judgeTaskId) {
        if (StrUtil.isBlank(judgeTaskId)) {
            return null;
        }
        return outboxMapper.selectOne(new LambdaQueryWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getJudgeTaskId, judgeTaskId)
                .orderByDesc(JudgeTaskOutbox::getId)
                .last("limit 1"));
    }

    private boolean hasActiveLease(String payload) {
        JudgeTaskMessage message = parsePayload(payload);
        if (message == null || StrUtil.isBlank(message.getSubmissionId())) {
            return false;
        }
        JudgeTaskExecution execution = executionMapper.selectOne(new LambdaQueryWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getSubmissionId, message.getSubmissionId())
                .last("limit 1"));
        if (execution == null) {
            return false;
        }
        boolean leaseStatus = JudgeTaskExecutionStatusConstant.LEASED.equals(execution.getStatus())
                || JudgeTaskExecutionStatusConstant.RUNNING.equals(execution.getStatus());
        return leaseStatus && execution.getLeaseUntil() != null
                && execution.getLeaseUntil() > System.currentTimeMillis();
    }

    private JudgeTaskMessage parsePayload(String payload) {
        if (StrUtil.isBlank(payload)) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, JudgeTaskMessage.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveJudgeMode(JudgeTaskExecution execution, JudgeTaskOutbox outbox) {
        JudgeTaskMessage message = parsePayload(outbox.getPayload());
        if (message != null && StrUtil.isNotBlank(message.getJudgeMode())) {
            return message.getJudgeMode();
        }
        return execution.getJudgeMode();
    }

    private void pushTerminalEvent(RecoveryPlan plan) {
        Judge judge = judgeMapper.selectById(plan.judgeId());
        JudgeResultEventRequest event = new JudgeResultEventRequest();
        event.setEventType(EVENT_JUDGE_FAILED);
        event.setSubmissionId(plan.submissionId());
        event.setJudgeTaskId(plan.judgeTaskId());
        event.setStatus(SubmissionStatusConstant.SYSTEM_ERROR);
        event.setStatusText(SubmissionStatusConstant.toText(SubmissionStatusConstant.SYSTEM_ERROR));
        event.setScore(0);
        event.setMessage(plan.message());
        event.setEventTime(OffsetDateTime.now());
        if (judge != null) {
            event.setTotalCase(defaultZero(judge.getTotalCase()));
            event.setJudgedCase(defaultZero(judge.getJudgedCase()));
            event.setCurrentCase(defaultZero(judge.getCurrentCase()));
        }
        try {
            messagingTemplate.convertAndSend(PROGRESS_TOPIC_PREFIX + plan.submissionId() + PROGRESS_TOPIC_SUFFIX, event);
        } catch (Exception e) {
            log.warn("Push recovery terminal event failed, submissionId: {}", plan.submissionId(), e);
        }
    }

    private int batchSize() {
        Integer value = streamProperties.getRecoveryBatchSize();
        return value == null || value <= 0 ? DEFAULT_BATCH_SIZE : value;
    }

    private int leaseBatchSize() {
        Integer value = streamProperties.getRecoveryLeaseBatchSize();
        return value == null || value <= 0 ? DEFAULT_LEASE_BATCH_SIZE : value;
    }

    private long strandedSeconds() {
        Long value = streamProperties.getStrandedQueuedSeconds();
        return value == null || value <= 0 ? DEFAULT_STRANDED_SECONDS : value;
    }

    private long orphanMinIdleMillis() {
        Long value = streamProperties.getOrphanPendingMinIdleMillis();
        return value == null || value <= 0 ? 300000L : value;
    }

    private int defaultMaxAttemptCount() {
        Integer value = streamProperties.getMaxAttemptCount();
        return value == null || value <= 0 ? 3 : value;
    }

    private int defaultMaxOutboxRetryCount() {
        // Outbox 未持久化上限时的兜底，与发布器默认 max_retry_count 对齐
        return DEFAULT_MAX_OUTBOX_RETRY_COUNT;
    }

    private long redispatchBackoffSeconds() {
        Long value = streamProperties.getStrandedQueuedSeconds();
        return value == null || value <= 0 ? DEFAULT_STRANDED_SECONDS : value;
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * @Author: HaoRan_Lyu
     * @Date: 2026/09/18
     * @Description: 恢复动作计划：终态或重派
     */
    private record RecoveryPlan(Long executionId, Long outboxId, Long judgeId, String submissionId, String judgeTaskId,
                                String streamKey, String payload, boolean terminal, String message) {

        static RecoveryPlan terminal(JudgeTaskExecution execution, String message) {
            return new RecoveryPlan(execution.getId(), null, execution.getJudgeId(), execution.getSubmissionId(),
                    execution.getJudgeTaskId(), null, null, true, message);
        }

        static RecoveryPlan redispatch(Long executionId, Long outboxId, String submissionId, String judgeTaskId,
                                       String streamKey, String payload) {
            return new RecoveryPlan(executionId, outboxId, null, submissionId, judgeTaskId, streamKey, payload,
                    false, null);
        }
    }
}
