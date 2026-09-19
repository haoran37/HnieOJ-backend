package com.hnieacm.submission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.dto.JudgeTaskMessage;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.judge.NodeProtocolConstants;
import com.hnieacm.common.properties.JudgeStreamProperties;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.service.NodeIdentityService;
import com.hnieacm.judge.service.SignedCaller;
import com.hnieacm.submission.constant.JudgeTaskExecutionStatusConstant;
import com.hnieacm.submission.dto.JudgeTaskLeaseRequest;
import com.hnieacm.submission.dto.JudgeTaskResumeRequest;
import com.hnieacm.submission.dto.JudgeTaskStreamEntry;
import com.hnieacm.submission.entity.JudgeTaskExecution;
import com.hnieacm.submission.mapper.JudgeTaskExecutionMapper;
import com.hnieacm.submission.service.JudgeTaskClaimService;
import com.hnieacm.submission.service.JudgeTaskStreamService;
import com.hnieacm.submission.service.impl.JudgeTaskLeaseManager.ResumeDecision;
import com.hnieacm.submission.vo.JudgeTaskClaimVo;
import com.hnieacm.submission.vo.JudgeTaskLeaseVo;
import com.hnieacm.submission.vo.JudgeTaskResumeVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 判题任务领取服务实现：XREADGROUP 只负责分发，MySQL 行锁与数据库权威额度决定所有权。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeTaskClaimServiceImpl implements JudgeTaskClaimService {

    private static final String DEFAULT_JUDGE_MODE = "default";

    private final JudgeTaskStreamService streamService;
    private final JudgeStreamProperties streamProperties;
    private final JudgeTaskLeaseManager leaseManager;
    private final JudgeTaskExecutionMapper executionMapper;
    private final NodeIdentityService nodeIdentityService;
    private final ObjectMapper objectMapper;

    @Override
    public JudgeTaskClaimVo claim(SignedCaller caller) {
        if (caller == null) {
            return null;
        }
        JudgeNodeToken node = nodeIdentityService.requireSessionOwner(caller.nodeId(), caller.keyId(),
                caller.sessionEpoch(), caller.accessVersion(), true);
        // 排空既可能是 draining 标记也可能是规范状态，两者都必须阻止新任务领取（权威判定在行锁内重复执行）
        if (Boolean.TRUE.equals(node.getDraining())
                || NodeProtocolConstants.NODE_STATUS_DRAINING.equals(node.getStatus())) {
            return null;
        }
        if (!hasFreeSlot(node)) {
            // 额度已满时不消费 Stream 条目，避免无谓地 ACK 掉待分发任务
            return null;
        }
        List<String> modes = parseModes(node.getSupportedJudgeModes());
        int rounds = claimMaxRounds();
        for (int round = 0; round < rounds; round++) {
            boolean sawAnyEntry = false;
            for (String mode : modes) {
                String streamKey = streamService.resolveStreamKey(mode);
                List<JudgeTaskStreamEntry> entries;
                try {
                    // 每次只取一条，避免把未领取的条目长时间留在 PEL
                    entries = streamService.readNew(streamKey, caller.nodeId(), 1);
                } catch (Exception e) {
                    // Redis 故障时不返回任务也不丢任务，交由 Outbox/恢复流程补偿
                    log.warn("Read judge task stream failed, streamKey: {}, nodeId: {}",
                            streamKey, caller.nodeId(), e);
                    return null;
                }
                if (entries.isEmpty()) {
                    continue;
                }
                sawAnyEntry = true;
                for (JudgeTaskStreamEntry entry : entries) {
                    JudgeTaskClaimVo claimed = tryAcquire(entry, caller);
                    if (claimed != null) {
                        return claimed;
                    }
                }
            }
            if (!sawAnyEntry) {
                break;
            }
        }
        return null;
    }

    @Override
    public JudgeTaskLeaseVo renew(String submissionId, SignedCaller caller, JudgeTaskLeaseRequest request) {
        if (request == null || StrUtil.isBlank(request.getJudgeTaskId()) || StrUtil.isBlank(request.getAttemptId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "judgeTaskId/attemptId 不能为空");
        }
        JudgeTaskExecution execution = leaseManager.renew(submissionId, caller,
                request.getJudgeTaskId().trim(), request.getAttemptId().trim());
        return new JudgeTaskLeaseVo(execution.getLeaseUntil());
    }

    @Override
    public JudgeTaskResumeVo resume(JudgeTaskResumeRequest request, SignedCaller caller) {
        if (caller == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "节点未认证");
        }
        JudgeTaskResumeVo result = new JudgeTaskResumeVo();
        if (request == null || request.getAttempts() == null) {
            return result;
        }
        // 整批共享一次节点权威校验：节点被接管/吊销/到期时整批 fail-closed。
        nodeIdentityService.requireSessionOwner(caller.nodeId(), caller.keyId(),
                caller.sessionEpoch(), caller.accessVersion(), true);
        for (JudgeTaskResumeRequest.Attempt attempt : request.getAttempts()) {
            if (attempt == null || StrUtil.isBlank(attempt.getSubmissionId())
                    || StrUtil.isBlank(attempt.getJudgeTaskId()) || StrUtil.isBlank(attempt.getAttemptId())) {
                JudgeTaskResumeVo.Rejected rejected = new JudgeTaskResumeVo.Rejected();
                if (attempt != null) {
                    rejected.setSubmissionId(attempt.getSubmissionId());
                    rejected.setJudgeTaskId(attempt.getJudgeTaskId());
                    rejected.setAttemptId(attempt.getAttemptId());
                }
                rejected.setReason("submissionId/judgeTaskId/attemptId 不能为空");
                result.getRejected().add(rejected);
                continue;
            }
            String submissionId = attempt.getSubmissionId().trim();
            String judgeTaskId = attempt.getJudgeTaskId().trim();
            String attemptId = attempt.getAttemptId().trim();
            ResumeDecision decision = leaseManager.resume(submissionId, judgeTaskId, attemptId, caller);
            if (decision.resumed() || decision.completed()) {
                JudgeTaskExecution execution = decision.execution();
                JudgeTaskResumeVo.Resumed resumed = new JudgeTaskResumeVo.Resumed();
                resumed.setSubmissionId(submissionId);
                resumed.setJudgeTaskId(judgeTaskId);
                resumed.setAttemptId(attemptId);
                resumed.setLeaseUntil(execution.getLeaseUntil());
                resumed.setRenewAfterMillis(execution.getRenewAfterMillis());
                resumed.setCompleted(decision.completed());
                result.getResumed().add(resumed);
            } else {
                JudgeTaskResumeVo.Rejected rejected = new JudgeTaskResumeVo.Rejected();
                rejected.setSubmissionId(submissionId);
                rejected.setJudgeTaskId(judgeTaskId);
                rejected.setAttemptId(attemptId);
                rejected.setReason(decision.reason());
                result.getRejected().add(rejected);
            }
        }
        return result;
    }

    private JudgeTaskClaimVo tryAcquire(JudgeTaskStreamEntry entry, SignedCaller caller) {
        JudgeTaskMessage message = parseMessage(entry);
        if (message == null || StrUtil.isBlank(message.getSubmissionId())
                || StrUtil.isBlank(message.getJudgeTaskId())) {
            streamService.ack(entry.streamKey(), entry.recordId());
            return null;
        }
        JudgeTaskExecution execution = leaseManager.acquire(message.getSubmissionId(), message.getJudgeTaskId(),
                entry.streamKey(), entry.recordId(), message.getJudgeMode(), caller);
        if (execution == null) {
            // 已被其他所有者领取/已完成/额度已满，ACK 该分发条目即可；MySQL 仍是权威
            streamService.ack(entry.streamKey(), entry.recordId());
            return null;
        }
        JudgeTaskClaimVo vo = new JudgeTaskClaimVo();
        vo.setTask(message);
        vo.setAttemptId(execution.getAttemptId());
        vo.setLeaseUntil(execution.getLeaseUntil());
        vo.setRenewAfterMillis(execution.getRenewAfterMillis());
        log.info("Judge task claimed, submissionId: {}, judgeTaskId: {}, nodeId: {}, attemptId: {}",
                message.getSubmissionId(), message.getJudgeTaskId(), caller.nodeId(), execution.getAttemptId());
        return vo;
    }

    private JudgeTaskMessage parseMessage(JudgeTaskStreamEntry entry) {
        if (entry.payload() == null || entry.payload().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(entry.payload(), JudgeTaskMessage.class);
        } catch (Exception e) {
            log.warn("Parse judge task payload failed, streamKey: {}, recordId: {}, msg: {}",
                    entry.streamKey(), entry.recordId(), e.getMessage());
            return null;
        }
    }

    private int claimMaxRounds() {
        Integer value = streamProperties.getClaimMaxRounds();
        return value == null || value <= 0 ? 4 : value;
    }

    private boolean hasFreeSlot(JudgeNodeToken node) {
        int maxConcurrency = node.getMaxConcurrency() == null || node.getMaxConcurrency() <= 0
                ? 1 : node.getMaxConcurrency();
        Long active = executionMapper.selectCount(new LambdaQueryWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getNodeId, node.getNodeId())
                .in(JudgeTaskExecution::getStatus,
                        JudgeTaskExecutionStatusConstant.LEASED,
                        JudgeTaskExecutionStatusConstant.RUNNING)
                .gt(JudgeTaskExecution::getLeaseUntil, System.currentTimeMillis()));
        return active == null || active < maxConcurrency;
    }

    private List<String> parseModes(String modes) {
        if (StrUtil.isBlank(modes)) {
            return new ArrayList<>(List.of(DEFAULT_JUDGE_MODE));
        }
        List<String> parsed = new ArrayList<>();
        for (String mode : modes.split(",")) {
            String trimmed = mode.trim();
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        return parsed.isEmpty() ? new ArrayList<>(List.of(DEFAULT_JUDGE_MODE)) : parsed;
    }
}
