package com.hnieacm.submission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.dto.JudgeTaskMessage;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.properties.JudgeStreamProperties;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.vo.JudgeNodeIdentity;
import com.hnieacm.submission.constant.JudgeTaskExecutionStatusConstant;
import com.hnieacm.submission.dto.JudgeTaskLeaseRequest;
import com.hnieacm.submission.dto.JudgeTaskStreamEntry;
import com.hnieacm.submission.entity.JudgeTaskExecution;
import com.hnieacm.submission.mapper.JudgeTaskExecutionMapper;
import com.hnieacm.submission.service.JudgeTaskClaimService;
import com.hnieacm.submission.service.JudgeTaskStreamService;
import com.hnieacm.submission.vo.JudgeTaskClaimVo;
import com.hnieacm.submission.vo.JudgeTaskLeaseVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 判题任务领取服务实现：XREADGROUP 只负责分发，MySQL 行锁决定所有权
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
    private final ObjectMapper objectMapper;

    @Override
    public JudgeTaskClaimVo claim(JudgeNodeIdentity identity) {
        if (identity == null || Boolean.TRUE.equals(identity.getDraining())) {
            return null;
        }
        if (!hasFreeSlot(identity)) {
            // 额度已满时不消费 Stream 条目，避免无谓地 ACK 掉待分发任务
            return null;
        }
        List<String> modes = identity.getSupportedJudgeModes();
        if (modes == null || modes.isEmpty()) {
            modes = List.of(DEFAULT_JUDGE_MODE);
        }
        int rounds = claimMaxRounds();
        for (int round = 0; round < rounds; round++) {
            boolean sawAnyEntry = false;
            for (String mode : modes) {
                String streamKey = streamService.resolveStreamKey(mode);
                List<JudgeTaskStreamEntry> entries;
                try {
                    // 每次只取一条，避免把未领取的条目长时间留在 PEL
                    entries = streamService.readNew(streamKey, identity.getNodeId(), 1);
                } catch (Exception e) {
                    // Redis 故障时不返回任务也不丢任务，交由 Outbox/恢复流程补偿
                    log.warn("Read judge task stream failed, streamKey: {}, nodeId: {}",
                            streamKey, identity.getNodeId(), e);
                    return null;
                }
                if (entries.isEmpty()) {
                    continue;
                }
                sawAnyEntry = true;
                for (JudgeTaskStreamEntry entry : entries) {
                    JudgeTaskClaimVo claimed = tryAcquire(entry, identity);
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
    public JudgeTaskLeaseVo renew(String submissionId, JudgeNodeIdentity identity, JudgeTaskLeaseRequest request) {
        if (request == null || StrUtil.isBlank(request.getJudgeTaskId()) || StrUtil.isBlank(request.getAttemptId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "judgeTaskId/attemptId 不能为空");
        }
        JudgeTaskExecution execution = leaseManager.renew(submissionId, identity,
                request.getJudgeTaskId().trim(), request.getAttemptId().trim());
        return new JudgeTaskLeaseVo(execution.getLeaseUntil());
    }

    private JudgeTaskClaimVo tryAcquire(JudgeTaskStreamEntry entry, JudgeNodeIdentity identity) {
        JudgeTaskMessage message = parseMessage(entry);
        if (message == null || StrUtil.isBlank(message.getSubmissionId())
                || StrUtil.isBlank(message.getJudgeTaskId())) {
            streamService.ack(entry.streamKey(), entry.recordId());
            return null;
        }
        JudgeTaskExecution execution = leaseManager.acquire(message.getSubmissionId(), message.getJudgeTaskId(),
                entry.streamKey(), entry.recordId(), message.getJudgeMode(), identity);
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
                message.getSubmissionId(), message.getJudgeTaskId(), identity.getNodeId(), execution.getAttemptId());
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

    private boolean hasFreeSlot(JudgeNodeIdentity identity) {
        int maxConcurrency = identity.getMaxConcurrency() == null || identity.getMaxConcurrency() <= 0
                ? 1 : identity.getMaxConcurrency();
        Long active = executionMapper.selectCount(new LambdaQueryWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getNodeId, identity.getNodeId())
                .in(JudgeTaskExecution::getStatus,
                        JudgeTaskExecutionStatusConstant.LEASED,
                        JudgeTaskExecutionStatusConstant.RUNNING)
                .gt(JudgeTaskExecution::getLeaseUntil, System.currentTimeMillis()));
        return active == null || active < maxConcurrency;
    }
}
