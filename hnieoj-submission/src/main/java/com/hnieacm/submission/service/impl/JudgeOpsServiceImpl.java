package com.hnieacm.submission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.submission.constant.JudgeTaskOutboxStatusConstant;
import com.hnieacm.submission.constant.SubmissionStatusConstant;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeTaskOutbox;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.mapper.JudgeTaskOutboxMapper;
import com.hnieacm.submission.properties.SubmissionProperties;
import com.hnieacm.submission.service.JudgeOpsService;
import com.hnieacm.submission.vo.JudgeOpsSummaryVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: Judge operation service implementation.
 */
@Service
@RequiredArgsConstructor
public class JudgeOpsServiceImpl implements JudgeOpsService {

    private static final List<String> OUTBOX_STATUSES = List.of(
            JudgeTaskOutboxStatusConstant.PENDING,
            JudgeTaskOutboxStatusConstant.PROCESSING,
            JudgeTaskOutboxStatusConstant.SENT,
            JudgeTaskOutboxStatusConstant.FAILED,
            JudgeTaskOutboxStatusConstant.EXHAUSTED
    );

    private static final List<Integer> JUDGING_STATUSES = List.of(
            SubmissionStatusConstant.PENDING,
            SubmissionStatusConstant.COMPILING,
            SubmissionStatusConstant.RUNNING
    );

    private final JudgeTaskOutboxMapper outboxMapper;
    private final JudgeMapper judgeMapper;
    private final SubmissionProperties submissionProperties;

    @Override
    public JudgeOpsSummaryVo summary() {
        LocalDateTime now = LocalDateTime.now();
        SubmissionProperties.JudgeTimeout timeout = submissionProperties.getJudgeTimeout();
        long pendingTimeoutSeconds = positiveOrDefault(timeout.getPendingTimeoutSeconds(), 1800L);
        long activeTimeoutSeconds = positiveOrDefault(timeout.getActiveTimeoutSeconds(), 1800L);
        long longPendingWarnSeconds = positiveOrDefault(timeout.getSentPendingWarnSeconds(), 3600L);

        Map<String, Long> outboxStatusCounts = countOutboxStatuses();
        Map<Integer, Long> judgingStatusCounts = countJudgingStatuses();
        long stalePendingCount = countPendingBefore(now.minusSeconds(pendingTimeoutSeconds));
        long staleActiveCount = countActiveBefore(now.minusSeconds(activeTimeoutSeconds));
        long longPendingWarnCount = countPendingBefore(now.minusSeconds(longPendingWarnSeconds));

        JudgeOpsSummaryVo vo = new JudgeOpsSummaryVo();
        vo.setGeneratedAt(now);
        vo.setOutboxStatusCounts(outboxStatusCounts);
        vo.setAbnormalOutboxCount(outboxStatusCounts.get(JudgeTaskOutboxStatusConstant.FAILED)
                + outboxStatusCounts.get(JudgeTaskOutboxStatusConstant.EXHAUSTED));
        vo.setExhaustedOutboxCount(outboxStatusCounts.get(JudgeTaskOutboxStatusConstant.EXHAUSTED));
        vo.setRetryableOutboxCount(outboxStatusCounts.get(JudgeTaskOutboxStatusConstant.PENDING)
                + outboxStatusCounts.get(JudgeTaskOutboxStatusConstant.FAILED));
        vo.setJudgingStatusCounts(judgingStatusCounts);
        vo.setJudgingSubmissionCount(judgingStatusCounts.values().stream().mapToLong(Long::longValue).sum());
        vo.setStalePendingCount(stalePendingCount);
        vo.setStaleActiveCount(staleActiveCount);
        vo.setLongPendingWarnCount(longPendingWarnCount);
        vo.setPendingTimeoutSeconds(pendingTimeoutSeconds);
        vo.setActiveTimeoutSeconds(activeTimeoutSeconds);
        vo.setLongPendingWarnSeconds(longPendingWarnSeconds);
        vo.setWarnings(buildWarnings(vo));
        vo.setHealthy(vo.getWarnings().isEmpty());
        return vo;
    }

    private Map<String, Long> countOutboxStatuses() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String status : OUTBOX_STATUSES) {
            result.put(status, outboxMapper.selectCount(new LambdaQueryWrapper<JudgeTaskOutbox>()
                    .eq(JudgeTaskOutbox::getStatus, status)));
        }
        return result;
    }

    private Map<Integer, Long> countJudgingStatuses() {
        Map<Integer, Long> result = new LinkedHashMap<>();
        for (Integer status : JUDGING_STATUSES) {
            result.put(status, judgeMapper.selectCount(new LambdaQueryWrapper<Judge>()
                    .eq(Judge::getStatus, status)));
        }
        return result;
    }

    private long countPendingBefore(LocalDateTime threshold) {
        return judgeMapper.selectCount(new LambdaQueryWrapper<Judge>()
                .eq(Judge::getStatus, SubmissionStatusConstant.PENDING)
                .le(Judge::getGmtModified, threshold));
    }

    private long countActiveBefore(LocalDateTime threshold) {
        return judgeMapper.selectCount(new LambdaQueryWrapper<Judge>()
                .in(Judge::getStatus, List.of(SubmissionStatusConstant.COMPILING, SubmissionStatusConstant.RUNNING))
                .le(Judge::getGmtModified, threshold));
    }

    private List<String> buildWarnings(JudgeOpsSummaryVo vo) {
        List<String> warnings = new ArrayList<>();
        if (vo.getExhaustedOutboxCount() > 0) {
            warnings.add("outbox_exhausted");
        }
        if (vo.getAbnormalOutboxCount() > 0) {
            warnings.add("outbox_abnormal");
        }
        if (vo.getStalePendingCount() > 0) {
            warnings.add("stale_pending_submission");
        }
        if (vo.getStaleActiveCount() > 0) {
            warnings.add("stale_active_submission");
        }
        if (vo.getLongPendingWarnCount() > 0) {
            warnings.add("long_pending_submission");
        }
        return warnings;
    }

    private long positiveOrDefault(Long value, long defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }
}
