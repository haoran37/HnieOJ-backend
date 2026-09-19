package com.hnieacm.submission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.judge.NodeProtocolConstants;
import com.hnieacm.common.properties.JudgeStreamProperties;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.service.NodeIdentityService;
import com.hnieacm.judge.service.SignedCaller;
import com.hnieacm.submission.constant.JudgeTaskExecutionStatusConstant;
import com.hnieacm.submission.entity.JudgeTaskExecution;
import com.hnieacm.submission.mapper.JudgeTaskExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 判题任务租约事务管理器：节点行锁 -> 任务行锁的固定顺序，保证额度、会话纪元与所有权转移串行化。
 *
 * <p>身份事实复用 {@link NodeIdentityService}，不新增第二套注册事实；关键写入先锁节点当前行，
 * 再校验 node/key/sessionEpoch/accessVersion/状态/到期，最后锁 task 写入。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeTaskLeaseManager {

    private static final int DEFAULT_MAX_CONCURRENCY = 1;
    private static final String DEFAULT_JUDGE_MODE = "default";
    private static final Set<String> FIXED_JUDGE_MODES = Set.of("default", "spj", "interactive");
    /** 节点硬截止与数据库 DATETIME 均按 +08:00 解释，与 NodeIdentityServiceImpl 保持一致。 */
    private static final ZoneOffset ZONE = ZoneOffset.ofHours(8);

    private final JudgeTaskExecutionMapper executionMapper;
    private final NodeIdentityService nodeIdentityService;
    private final JudgeStreamProperties streamProperties;

    /**
     * @MethodName acquire
     * @Param submissionId 提交展示 ID
     * @Param judgeTaskId  判题任务 ID
     * @Param streamKey    分发 Stream key
     * @Param recordId     Stream 消息 ID
     * @Param judgeMode    判题模式
     * @Param caller       已认证节点会话
     * @Description 在 Node 行锁下按数据库权威额度抢占 queued 执行记录；并发只有一个所有者
     * @Return @return {@link JudgeTaskExecution }；不可领取时返回 null
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Transactional(rollbackFor = Exception.class)
    public JudgeTaskExecution acquire(String submissionId, String judgeTaskId, String streamKey, String recordId,
                                      String judgeMode, SignedCaller caller) {
        JudgeNodeToken node = lockOwnedNode(caller);
        if (node == null || isNodeDraining(node)) {
            return null;
        }
        // 计时放在取得节点锁之后，避免等待锁期间租约已到期却被旧时间重新续活
        long now = System.currentTimeMillis();
        if (!isAuthorizedMode(node, judgeMode)) {
            return null;
        }
        int maxConcurrency = resolveMaxConcurrency(node);
        Long activeLeases = executionMapper.selectCount(new LambdaQueryWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getNodeId, caller.nodeId())
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
        // 硬截止取任务执行截止、节点授权截止与节点身份到期的较小值：租约绝不允许越过任何硬边界。
        long hardDeadline = resolveHardDeadline(execution, node);
        if (hardDeadline <= now) {
            return null;
        }
        int attemptCount = defaultZero(execution.getAttemptCount()) + 1;
        int maxAttemptCount = resolveMaxAttemptCount(execution);
        if (attemptCount > maxAttemptCount) {
            return null;
        }

        String attemptId = newId();
        long leaseUntil = Math.min(now + leaseMillis(), hardDeadline);
        int renewAfterMillis = boundedRenewAfterMillis(now, leaseUntil);
        executionMapper.update(null, new LambdaUpdateWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getId, execution.getId())
                .set(JudgeTaskExecution::getNodeId, caller.nodeId())
                .set(JudgeTaskExecution::getTokenId, caller.nodeId())
                .set(JudgeTaskExecution::getSessionEpoch, caller.sessionEpoch())
                .set(JudgeTaskExecution::getAttemptId, attemptId)
                .set(JudgeTaskExecution::getAttemptCount, attemptCount)
                .set(JudgeTaskExecution::getStatus, JudgeTaskExecutionStatusConstant.LEASED)
                .set(JudgeTaskExecution::getLeaseUntil, leaseUntil)
                .set(JudgeTaskExecution::getRenewAfterMillis, renewAfterMillis)
                .set(JudgeTaskExecution::getStreamKey, streamKey)
                .set(JudgeTaskExecution::getStreamId, recordId)
                .set(JudgeTaskExecution::getLastError, null));

        execution.setNodeId(caller.nodeId());
        execution.setTokenId(caller.nodeId());
        execution.setSessionEpoch(caller.sessionEpoch());
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
     * @Param submissionId 提交展示 ID
     * @Param caller       已认证节点会话
     * @Param judgeTaskId  判题任务 ID
     * @Param attemptId    当前尝试 ID
     * @Description 续租：仅当前会话所有权/轮次/未过期租约/未超硬截止可续，禁止过期租约复活
     * @Return @return {@link JudgeTaskExecution }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Transactional(rollbackFor = Exception.class)
    public JudgeTaskExecution renew(String submissionId, SignedCaller caller, String judgeTaskId, String attemptId) {
        JudgeNodeToken node = lockOwnedNodeOrThrow(caller);
        long now = System.currentTimeMillis();
        JudgeTaskExecution execution = executionMapper.selectBySubmissionIdForUpdate(submissionId);
        if (execution == null || !ownershipMatches(execution, caller, judgeTaskId, attemptId)) {
            throw new BizException(ResultCode.FORBIDDEN, "判题任务所有者不匹配");
        }
        if (!isLeaseStatus(execution) || !isActiveLease(execution, now)) {
            throw new BizException(ResultCode.FORBIDDEN, "判题任务租约已失效");
        }
        // 续租同样不得越过任务执行截止与节点硬截止；剩余寿命不足时直接拒绝，禁止无界延长。
        long hardDeadline = resolveHardDeadline(execution, node);
        long leaseUntil = Math.min(now + leaseMillis(), hardDeadline);
        if (leaseUntil <= now) {
            throw new BizException(ResultCode.FORBIDDEN, "判题任务已超过硬截止时间");
        }
        executionMapper.update(null, new LambdaUpdateWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getId, execution.getId())
                .set(JudgeTaskExecution::getLeaseUntil, leaseUntil));
        execution.setLeaseUntil(leaseUntil);
        return execution;
    }

    /**
     * @MethodName validateEventOwnership
     * @Param submissionId   提交展示 ID
     * @Param caller         已认证节点会话
     * @Param judgeTaskId    判题任务 ID
     * @Param attemptId      当前尝试 ID
     * @Param allowCompleted 是否允许已完成终态重放
     * @Description 事件写入前在节点/任务行锁内校验身份/会话纪元/轮次/尝试/租约
     * @Return @return {@link JudgeTaskExecution }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Transactional(rollbackFor = Exception.class)
    public JudgeTaskExecution validateEventOwnership(String submissionId, SignedCaller caller,
                                                     String judgeTaskId, String attemptId, boolean allowCompleted) {
        lockOwnedNodeOrThrow(caller);
        long now = System.currentTimeMillis();
        JudgeTaskExecution execution = executionMapper.selectBySubmissionIdForUpdate(submissionId);
        if (execution == null || !ownershipMatches(execution, caller, judgeTaskId, attemptId)) {
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
     * @MethodName resume
     * @Param submissionId 提交展示 ID
     * @Param judgeTaskId  判题任务 ID
     * @Param attemptId    待恢复尝试 ID
     * @Param caller       重连后的新会话
     * @Description 合法未过期同一 attempt 迁移 sessionEpoch 到当前会话且不增加执行预算；
     * 已完成返回 completed；过期/不匹配一律拒绝，绝不复活过期 attempt。
     * @Return @return {@link ResumeDecision }
     * @Author HaoRan_Lyu
     * @Date 2026/09/19
     */
    @Transactional(rollbackFor = Exception.class)
    public ResumeDecision resume(String submissionId, String judgeTaskId, String attemptId, SignedCaller caller) {
        lockOwnedNodeOrThrow(caller);
        long now = System.currentTimeMillis();
        JudgeTaskExecution execution = executionMapper.selectBySubmissionIdForUpdate(submissionId);
        if (execution == null) {
            return ResumeDecision.rejected("执行记录不存在");
        }
        if (!Objects.equals(execution.getJudgeTaskId(), judgeTaskId)
                || !Objects.equals(execution.getAttemptId(), attemptId)
                || !Objects.equals(execution.getNodeId(), caller.nodeId())) {
            return ResumeDecision.rejected("attempt 所有权不匹配");
        }
        if (JudgeTaskExecutionStatusConstant.COMPLETED.equals(execution.getStatus())) {
            // 终态也迁移会话绑定：重连后结果重放必须由当前权威会话提交，内容仍由指纹校验
            migrateSession(execution, attemptId, caller);
            return ResumeDecision.completed(execution);
        }
        if (JudgeTaskExecutionStatusConstant.FAILED.equals(execution.getStatus())) {
            return ResumeDecision.rejected("执行已终止");
        }
        if (!isLeaseStatus(execution) || !isActiveLease(execution, now)) {
            return ResumeDecision.rejected("租约已过期");
        }
        if (execution.getExecutionDeadline() != null && execution.getExecutionDeadline() <= now) {
            return ResumeDecision.rejected("已超过执行截止时间");
        }
        // 仅迁移会话纪元/令牌绑定；attemptId、attemptCount、执行预算与租约时间保持不变
        migrateSession(execution, attemptId, caller);
        return ResumeDecision.resumed(execution);
    }

    private void migrateSession(JudgeTaskExecution execution, String attemptId, SignedCaller caller) {
        executionMapper.update(null, new LambdaUpdateWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getId, execution.getId())
                .eq(JudgeTaskExecution::getAttemptId, attemptId)
                .set(JudgeTaskExecution::getSessionEpoch, caller.sessionEpoch())
                .set(JudgeTaskExecution::getTokenId, caller.nodeId()));
        execution.setSessionEpoch(caller.sessionEpoch());
        execution.setTokenId(caller.nodeId());
    }

    /**
     * @MethodName markRunning
     * @Param execution 执行记录
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
     * @Param execution           执行记录
     * @Param terminalFingerprint 终态业务指纹
     * @Description 终态结果落库后标记完成并写入指纹，解除额度占用；同事务保证回滚不落指纹
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
     * @Param submissionId 提交展示 ID
     * @Param judgeTaskId  判题任务 ID
     * @Param attemptId    尝试 ID
     * @Param problemId    题目 ID
     * @Param caller       已认证节点会话
     * @Description 测试数据下载资格：身份与有效租约、任务绑定 problemId 相符
     * @Return @return {@link JudgeTaskExecution }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Transactional(rollbackFor = Exception.class)
    public JudgeTaskExecution validateDownloadAccess(String submissionId, String judgeTaskId, String attemptId,
                                                     Long problemId, SignedCaller caller) {
        if (submissionId == null || judgeTaskId == null || attemptId == null || problemId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "submissionId/judgeTaskId/attemptId/problemId 不能为空");
        }
        lockOwnedNodeOrThrow(caller);
        long now = System.currentTimeMillis();
        JudgeTaskExecution execution = executionMapper.selectBySubmissionIdForUpdate(submissionId);
        if (execution == null || !ownershipMatches(execution, caller, judgeTaskId, attemptId)) {
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

    private JudgeNodeToken lockOwnedNode(SignedCaller caller) {
        if (caller == null || caller.nodeId() == null || caller.keyId() == null) {
            return null;
        }
        return nodeIdentityService.lockSessionOwner(caller.nodeId(), caller.keyId(),
                caller.sessionEpoch(), caller.accessVersion(), true);
    }

    private JudgeNodeToken lockOwnedNodeOrThrow(SignedCaller caller) {
        JudgeNodeToken node = lockOwnedNode(caller);
        if (node == null) {
            throw new BizException(ResultCode.FORBIDDEN, "判题节点凭证无效");
        }
        return node;
    }

    /**
     * 节点是否处于排空状态：{@code draining} 标记或规范状态 {@code NODE_STATUS_DRAINING} 任一命中即排空。
     * 新任务领取必须同时尊重两种指示，避免仅改状态或仅改标记的路径漏判。
     */
    private boolean isNodeDraining(JudgeNodeToken node) {
        return Boolean.TRUE.equals(node.getDraining())
                || NodeProtocolConstants.NODE_STATUS_DRAINING.equals(node.getStatus());
    }

    /**
     * 计算任务可运行的硬截止：任务执行截止、节点授权截止、节点身份到期取最小。
     *
     * @return 最早硬截止的 epoch 毫秒；未设置任何截止时返回 {@link Long#MAX_VALUE}
     */
    private long resolveHardDeadline(JudgeTaskExecution execution, JudgeNodeToken node) {
        long deadline = Long.MAX_VALUE;
        if (execution.getExecutionDeadline() != null) {
            deadline = Math.min(deadline, execution.getExecutionDeadline());
        }
        if (node.getAuthorizationUntil() != null) {
            deadline = Math.min(deadline, toEpoch(node.getAuthorizationUntil()));
        }
        if (node.getExpireTime() != null) {
            deadline = Math.min(deadline, toEpoch(node.getExpireTime()));
        }
        return deadline;
    }

    /**
     * 将续租提示压缩到实际剩余租期内，保证节点不会在硬截止之后才被要求续租。
     */
    private int boundedRenewAfterMillis(long now, long leaseUntil) {
        long remaining = leaseUntil - now;
        if (remaining <= 0) {
            return 1;
        }
        long bounded = Math.min(renewAfterMillis(), remaining);
        return bounded < 1 ? 1 : (int) Math.min(bounded, Integer.MAX_VALUE);
    }

    private static long toEpoch(LocalDateTime time) {
        return time == null ? 0L : time.toInstant(ZONE).toEpochMilli();
    }

    private boolean ownershipMatches(JudgeTaskExecution execution, SignedCaller caller,
                                     String judgeTaskId, String attemptId) {
        if (!Objects.equals(execution.getNodeId(), caller.nodeId())
                || !Objects.equals(execution.getAttemptId(), attemptId)
                || !Objects.equals(execution.getJudgeTaskId(), judgeTaskId)) {
            return false;
        }
        // 会话纪元权威：只有显式匹配的纪元才允许续租/回传结果；null 纪元记录仅存在于领取前的 queued 行，
        // 不存在向后兼容的 null 放行通道（最终切换无遗留执行模式）。
        Long executionEpoch = execution.getSessionEpoch();
        return executionEpoch != null && caller.sessionEpoch() > 0
                && executionEpoch.longValue() == caller.sessionEpoch();
    }

    private boolean isActiveLease(JudgeTaskExecution execution, long now) {
        return isLeaseStatus(execution) && execution.getLeaseUntil() != null && execution.getLeaseUntil() > now;
    }

    private boolean isLeaseStatus(JudgeTaskExecution execution) {
        return JudgeTaskExecutionStatusConstant.LEASED.equals(execution.getStatus())
                || JudgeTaskExecutionStatusConstant.RUNNING.equals(execution.getStatus());
    }

    private int resolveMaxConcurrency(JudgeNodeToken node) {
        if (node.getMaxConcurrency() != null && node.getMaxConcurrency() > 0) {
            return node.getMaxConcurrency();
        }
        return DEFAULT_MAX_CONCURRENCY;
    }

    /**
     * @MethodName isAuthorizedMode
     * @Param node      数据库权威节点事实
     * @Param judgeMode 判题模式
     * @Description 领取时在节点行锁内核对服务端批准的模式；必须属于固定集合且在该节点授权集合内
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    private boolean isAuthorizedMode(JudgeNodeToken node, String judgeMode) {
        String normalizedMode = judgeMode == null || judgeMode.isBlank()
                ? DEFAULT_JUDGE_MODE : judgeMode.trim().toLowerCase(Locale.ROOT);
        if (!FIXED_JUDGE_MODES.contains(normalizedMode)) {
            return false;
        }
        String supported = node.getSupportedJudgeModes();
        if (supported == null || supported.isBlank()) {
            return DEFAULT_JUDGE_MODE.equals(normalizedMode);
        }
        for (String mode : supported.split(",")) {
            if (normalizedMode.equals(mode.trim().toLowerCase(Locale.ROOT))) {
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

    /**
     * @Author: HaoRan_Lyu
     * @Date: 2026/09/19
     * @Description: RESUME 决策：resumed/ completed/ rejected 三态
     */
    public record ResumeDecision(boolean resumed, boolean completed, JudgeTaskExecution execution, String reason) {

        static ResumeDecision resumed(JudgeTaskExecution execution) {
            return new ResumeDecision(true, false, execution, null);
        }

        static ResumeDecision completed(JudgeTaskExecution execution) {
            return new ResumeDecision(false, true, execution, null);
        }

        static ResumeDecision rejected(String reason) {
            return new ResumeDecision(false, false, null, reason);
        }
    }
}
