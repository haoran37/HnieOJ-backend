package com.hnieacm.submission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.properties.JudgeStreamProperties;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.constant.JudgeNodeConstant;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.vo.JudgeNodeIdentity;
import com.hnieacm.submission.constant.JudgeTaskExecutionStatusConstant;
import com.hnieacm.submission.entity.JudgeTaskExecution;
import com.hnieacm.submission.mapper.JudgeTaskExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 判题任务租约事务管理器：Node 行锁 -> Task 行锁的固定顺序，保证额度与所有权转移串行化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeTaskLeaseManager {

    private static final int DEFAULT_MAX_CONCURRENCY = 1;
    private static final String DEFAULT_JUDGE_MODE = "default";
    private static final Set<String> FIXED_JUDGE_MODES = Set.of("default", "spj", "interactive");

    private final JudgeTaskExecutionMapper executionMapper;
    private final JudgeNodeTokenMapper tokenMapper;
    private final JudgeStreamProperties streamProperties;

    /**
     * @MethodName acquire
     * @Param submissionId
     * @Param judgeTaskId
     * @Param identity
     * @Description 在 Node 行锁下校验额度并抢占无有效租约的执行记录；并发只有一个所有者
     * @Return @return {@link JudgeTaskExecution }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Transactional(rollbackFor = Exception.class)
    public JudgeTaskExecution acquire(String submissionId, String judgeTaskId, String streamKey, String recordId,
                                      String judgeMode, JudgeNodeIdentity identity) {
        JudgeNodeToken node = lockActiveNode(identity);
        if (node == null || Boolean.TRUE.equals(node.getDraining())) {
            return null;
        }
        // 计时放在取得节点锁之后，避免等待锁期间租约已到期却被旧时间重新续活
        long now = System.currentTimeMillis();
        if (!isAuthorizedMode(node, judgeMode)) {
            return null;
        }
        int maxConcurrency = resolveMaxConcurrency(node);
        Long activeLeases = executionMapper.selectCount(new LambdaQueryWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getNodeId, identity.getNodeId())
                .in(JudgeTaskExecution::getStatus,
                        JudgeTaskExecutionStatusConstant.LEASED,
                        JudgeTaskExecutionStatusConstant.RUNNING)
                .gt(JudgeTaskExecution::getLeaseUntil, now));
        if (activeLeases != null && activeLeases >= maxConcurrency) {
            return null;
        }

        JudgeTaskExecution execution = executionMapper.selectBySubmissionIdForUpdate(submissionId);
        if (execution == null || !Objects.equals(execution.getJudgeTaskId(), judgeTaskId)) {
            return null;
        }
        // 只有经过恢复转移的 queued 记录才能获得新尝试，避免重复消息绕过清测试点/进度/指标
        if (!JudgeTaskExecutionStatusConstant.QUEUED.equals(execution.getStatus())) {
            return null;
        }
        if (execution.getExecutionDeadline() != null && execution.getExecutionDeadline() <= now) {
            return null;
        }
        int attemptCount = defaultZero(execution.getAttemptCount()) + 1;
        int maxAttemptCount = resolveMaxAttemptCount(execution);
        if (attemptCount > maxAttemptCount) {
            return null;
        }

        String attemptId = newId();
        long leaseUntil = now + leaseMillis();
        int renewAfterMillis = renewAfterMillis();
        executionMapper.update(null, new LambdaUpdateWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getId, execution.getId())
                .set(JudgeTaskExecution::getNodeId, identity.getNodeId())
                .set(JudgeTaskExecution::getTokenId, identity.getTokenId())
                .set(JudgeTaskExecution::getAttemptId, attemptId)
                .set(JudgeTaskExecution::getAttemptCount, attemptCount)
                .set(JudgeTaskExecution::getStatus, JudgeTaskExecutionStatusConstant.LEASED)
                .set(JudgeTaskExecution::getLeaseUntil, leaseUntil)
                .set(JudgeTaskExecution::getRenewAfterMillis, renewAfterMillis)
                .set(JudgeTaskExecution::getStreamKey, streamKey)
                .set(JudgeTaskExecution::getStreamId, recordId)
                .set(JudgeTaskExecution::getLastError, null));

        execution.setNodeId(identity.getNodeId());
        execution.setTokenId(identity.getTokenId());
        execution.setAttemptId(attemptId);
        execution.setAttemptCount(attemptCount);
        execution.setStatus(JudgeTaskExecutionStatusConstant.LEASED);
        execution.setLeaseUntil(leaseUntil);
        execution.setRenewAfterMillis(renewAfterMillis);
        execution.setStreamKey(streamKey);
        execution.setStreamId(recordId);
        return execution;
    }

    /**
     * @MethodName renew
     * @Param submissionId
     * @Param identity
     * @Param judgeTaskId
     * @Param attemptId
     * @Description 续租：仅当前所有者/轮次/未过期租约/未超硬截止可续，禁止过期租约复活
     * @Return @return {@link JudgeTaskExecution }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Transactional(rollbackFor = Exception.class)
    public JudgeTaskExecution renew(String submissionId, JudgeNodeIdentity identity, String judgeTaskId, String attemptId) {
        JudgeNodeToken node = lockActiveNode(identity);
        if (node == null) {
            throw new BizException(ResultCode.FORBIDDEN, "判题节点凭证无效");
        }
        long now = System.currentTimeMillis();
        JudgeTaskExecution execution = executionMapper.selectBySubmissionIdForUpdate(submissionId);
        if (execution == null || !ownershipMatches(execution, identity, judgeTaskId, attemptId)) {
            throw new BizException(ResultCode.FORBIDDEN, "判题任务所有者不匹配");
        }
        if (!isLeaseStatus(execution) || !isActiveLease(execution, now)) {
            throw new BizException(ResultCode.FORBIDDEN, "判题任务租约已失效");
        }
        if (execution.getExecutionDeadline() != null && execution.getExecutionDeadline() <= now) {
            throw new BizException(ResultCode.FORBIDDEN, "判题任务已超过执行截止时间");
        }
        long leaseUntil = now + leaseMillis();
        executionMapper.update(null, new LambdaUpdateWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getId, execution.getId())
                .set(JudgeTaskExecution::getLeaseUntil, leaseUntil));
        execution.setLeaseUntil(leaseUntil);
        return execution;
    }

    /**
     * @MethodName validateEventOwnership
     * @Param submissionId
     * @Param identity
     * @Param judgeTaskId
     * @Param attemptId
     * @Param allowCompleted
     * @Description 事件写入前校验身份/轮次/尝试/租约；终态幂等允许已完成记录
     * @Return @return {@link JudgeTaskExecution }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Transactional(rollbackFor = Exception.class)
    public JudgeTaskExecution validateEventOwnership(String submissionId, JudgeNodeIdentity identity,
                                                     String judgeTaskId, String attemptId, boolean allowCompleted) {
        JudgeNodeToken node = lockActiveNode(identity);
        if (node == null) {
            throw new BizException(ResultCode.FORBIDDEN, "判题节点凭证无效");
        }
        long now = System.currentTimeMillis();
        JudgeTaskExecution execution = executionMapper.selectBySubmissionIdForUpdate(submissionId);
        if (execution == null || !ownershipMatches(execution, identity, judgeTaskId, attemptId)) {
            throw new BizException(ResultCode.FORBIDDEN, "判题任务所有者不匹配");
        }
        if (JudgeTaskExecutionStatusConstant.COMPLETED.equals(execution.getStatus())) {
            if (!allowCompleted) {
                throw new BizException(ResultCode.FORBIDDEN, "判题任务已完成");
            }
            return execution;
        }
        if (JudgeTaskExecutionStatusConstant.FAILED.equals(execution.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "判题任务已终止");
        }
        if (!isLeaseStatus(execution) || !isActiveLease(execution, now)) {
            throw new BizException(ResultCode.FORBIDDEN, "判题任务租约已失效");
        }
        if (execution.getExecutionDeadline() != null && execution.getExecutionDeadline() <= now) {
            throw new BizException(ResultCode.FORBIDDEN, "判题任务已超过执行截止时间");
        }
        return execution;
    }

    /**
     * @MethodName markRunning
     * @Param execution
     * @Description 进度事件后标记执行中
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Transactional(rollbackFor = Exception.class)
    public void markRunning(JudgeTaskExecution execution) {
        if (execution == null || JudgeTaskExecutionStatusConstant.COMPLETED.equals(execution.getStatus())) {
            return;
        }
        executionMapper.update(null, new LambdaUpdateWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getId, execution.getId())
                .in(JudgeTaskExecution::getStatus,
                        JudgeTaskExecutionStatusConstant.LEASED,
                        JudgeTaskExecutionStatusConstant.RUNNING)
                .set(JudgeTaskExecution::getStatus, JudgeTaskExecutionStatusConstant.RUNNING));
    }

    /**
     * @MethodName complete
     * @Param execution
     * @Param terminalFingerprint
     * @Description 终态结果落库后标记执行完成并写入终态业务指纹，解除额度占用；同一事务内写入保证回滚不落指纹
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Transactional(rollbackFor = Exception.class)
    public void complete(JudgeTaskExecution execution, String terminalFingerprint) {
        if (execution == null) {
            return;
        }
        executionMapper.update(null, new LambdaUpdateWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getId, execution.getId())
                .set(JudgeTaskExecution::getStatus, JudgeTaskExecutionStatusConstant.COMPLETED)
                .set(JudgeTaskExecution::getLeaseUntil, null)
                .set(JudgeTaskExecution::getTerminalFingerprint, terminalFingerprint));
    }

    /**
     * @MethodName validateDownloadAccess
     * @Param submissionId
     * @Param judgeTaskId
     * @Param attemptId
     * @Param problemId
     * @Param identity
     * @Description 测试数据下载资格：身份与有效租约、任务绑定 problemId 相符
     * @Return @return {@link JudgeTaskExecution }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Transactional(rollbackFor = Exception.class)
    public JudgeTaskExecution validateDownloadAccess(String submissionId, String judgeTaskId, String attemptId,
                                                     Long problemId, JudgeNodeIdentity identity) {
        if (submissionId == null || judgeTaskId == null || attemptId == null || problemId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "submissionId/judgeTaskId/attemptId/problemId 不能为空");
        }
        JudgeNodeToken node = lockActiveNode(identity);
        if (node == null) {
            throw new BizException(ResultCode.FORBIDDEN, "判题节点凭证无效");
        }
        long now = System.currentTimeMillis();
        JudgeTaskExecution execution = executionMapper.selectBySubmissionIdForUpdate(submissionId);
        if (execution == null || !ownershipMatches(execution, identity, judgeTaskId, attemptId)) {
            throw new BizException(ResultCode.FORBIDDEN, "判题任务所有者不匹配");
        }
        if (!Objects.equals(execution.getProblemId(), problemId)) {
            throw new BizException(ResultCode.FORBIDDEN, "判题任务与题目不匹配");
        }
        if (!isLeaseStatus(execution) || !isActiveLease(execution, now)) {
            throw new BizException(ResultCode.FORBIDDEN, "判题任务租约已失效");
        }
        if (execution.getExecutionDeadline() != null && execution.getExecutionDeadline() <= now) {
            throw new BizException(ResultCode.FORBIDDEN, "判题任务已超过执行截止时间");
        }
        return execution;
    }

    private JudgeNodeToken lockActiveNode(JudgeNodeIdentity identity) {
        if (identity == null || identity.getTokenId() == null) {
            return null;
        }
        JudgeNodeToken node = tokenMapper.selectByTokenIdForUpdate(identity.getTokenId());
        if (node == null || !JudgeNodeConstant.TOKEN_ACTIVE.equals(node.getStatus())) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (node.getExpireTime() == null
                || node.getExpireTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() <= now) {
            return null;
        }
        if (!Objects.equals(node.getNodeId(), identity.getNodeId())) {
            return null;
        }
        if (JudgeNodeConstant.NODE_TYPE_TEMP.equals(node.getNodeType())) {
            if (node.getAuthorizationUntil() == null
                    || node.getAuthorizationUntil().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() <= now) {
                return null;
            }
        }
        return node;
    }

    private boolean ownershipMatches(JudgeTaskExecution execution, JudgeNodeIdentity identity,
                                     String judgeTaskId, String attemptId) {
        return Objects.equals(execution.getNodeId(), identity.getNodeId())
                && Objects.equals(execution.getTokenId(), identity.getTokenId())
                && Objects.equals(execution.getAttemptId(), attemptId)
                && Objects.equals(execution.getJudgeTaskId(), judgeTaskId);
    }

    private boolean isActiveLease(JudgeTaskExecution execution, long now) {
        return isLeaseStatus(execution) && execution.getLeaseUntil() != null && execution.getLeaseUntil() > now;
    }

    private boolean isLeaseStatus(JudgeTaskExecution execution) {
        return JudgeTaskExecutionStatusConstant.LEASED.equals(execution.getStatus())
                || JudgeTaskExecutionStatusConstant.RUNNING.equals(execution.getStatus());
    }

    private int resolveMaxConcurrency(JudgeNodeToken node) {
        if (node.getApprovedMaxConcurrency() != null && node.getApprovedMaxConcurrency() > 0) {
            return node.getApprovedMaxConcurrency();
        }
        if (node.getMaxConcurrency() != null && node.getMaxConcurrency() > 0) {
            return node.getMaxConcurrency();
        }
        return DEFAULT_MAX_CONCURRENCY;
    }

    /**
     * @MethodName isAuthorizedMode
     * @Param node
     * @Param judgeMode
     * @Description 领取时在节点行锁内核对服务端批准的模式；模式必须属于固定集合且在该节点授权集合内
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    private boolean isAuthorizedMode(JudgeNodeToken node, String judgeMode) {
        String normalizedMode = judgeMode == null || judgeMode.isBlank()
                ? DEFAULT_JUDGE_MODE : judgeMode.trim().toLowerCase();
        if (!FIXED_JUDGE_MODES.contains(normalizedMode)) {
            return false;
        }
        String supported = node.getSupportedJudgeModes();
        if (supported == null || supported.isBlank()) {
            return DEFAULT_JUDGE_MODE.equals(normalizedMode);
        }
        for (String mode : supported.split(",")) {
            if (normalizedMode.equals(mode.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private int resolveMaxAttemptCount(JudgeTaskExecution execution) {
        if (execution.getMaxAttemptCount() != null && execution.getMaxAttemptCount() > 0) {
            return execution.getMaxAttemptCount();
        }
        Integer configured = streamProperties.getMaxAttemptCount();
        return configured == null || configured <= 0 ? 3 : configured;
    }

    private long leaseMillis() {
        Long seconds = streamProperties.getLeaseSeconds();
        long value = seconds == null || seconds <= 0 ? 60L : seconds;
        return value * 1000L;
    }

    private int renewAfterMillis() {
        Long value = streamProperties.getRenewAfterMillis();
        long millis = value == null || value <= 0 ? 20000L : value;
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
