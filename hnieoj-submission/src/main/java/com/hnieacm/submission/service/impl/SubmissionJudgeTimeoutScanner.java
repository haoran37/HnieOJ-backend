package com.hnieacm.submission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.submission.constant.JudgeTaskOutboxStatusConstant;
import com.hnieacm.submission.constant.SubmissionStatusConstant;
import com.hnieacm.submission.dto.JudgeResultEventRequest;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeTaskOutbox;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.mapper.JudgeTaskOutboxMapper;
import com.hnieacm.submission.properties.SubmissionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 卡住提交扫描服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionJudgeTimeoutScanner {

    private static final String EVENT_JUDGE_FAILED = "JUDGE_FAILED";
    private static final String PROGRESS_TOPIC_PREFIX = "/topic/submissions/";
    private static final String PROGRESS_TOPIC_SUFFIX = "/progress";
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final long DEFAULT_PENDING_TIMEOUT_SECONDS = 1800L;
    private static final long DEFAULT_ACTIVE_TIMEOUT_SECONDS = 1800L;

    private final JudgeMapper judgeMapper;
    private final JudgeTaskOutboxMapper outboxMapper;
    private final SubmissionProperties submissionProperties;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedDelayString = "${hnieoj.submission.judge-timeout.scan-interval-ms:60000}")
    public void scanTimeoutSubmissions() {
        if (!Boolean.TRUE.equals(submissionProperties.getJudgeTimeout().getEnabled())) {
            return;
        }
        scanStatus(SubmissionStatusConstant.PENDING, pendingTimeoutSeconds(), "Judge task pending timeout");
        scanStatus(SubmissionStatusConstant.COMPILING, activeTimeoutSeconds(), "Judge task compiling timeout");
        scanStatus(SubmissionStatusConstant.RUNNING, activeTimeoutSeconds(), "Judge task running timeout");
    }

    private void scanStatus(Integer status, long timeoutSeconds, String message) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusSeconds(timeoutSeconds);
        List<Judge> candidates = judgeMapper.selectList(new LambdaQueryWrapper<Judge>()
                .eq(Judge::getStatus, status)
                .le(Judge::getGmtModified, cutoffTime)
                .orderByAsc(Judge::getGmtModified)
                .last("limit " + batchSize()));
        for (Judge judge : candidates) {
            handleTimeoutJudge(judge, status, message);
        }
    }

    private void handleTimeoutJudge(Judge judge, Integer expectedStatus, String message) {
        if (hasActiveOutbox(judge.getJudgeTaskId())) {
            log.info("Submission timeout skipped because outbox is still active, submissionId: {}, judgeTaskId: {}",
                    judge.getSubmitId(), judge.getJudgeTaskId());
            return;
        }
        int updated = judgeMapper.update(null, new LambdaUpdateWrapper<Judge>()
                .eq(Judge::getId, judge.getId())
                .eq(Judge::getStatus, expectedStatus)
                .eq(StrUtil.isNotBlank(judge.getJudgeTaskId()), Judge::getJudgeTaskId, judge.getJudgeTaskId())
                .set(Judge::getStatus, SubmissionStatusConstant.SYSTEM_ERROR)
                .set(Judge::getScore, 0)
                .set(Judge::getErrorMessage, message));
        if (updated == 0) {
            return;
        }
        log.warn("Submission marked as system error by timeout scanner, submissionId: {}, judgeTaskId: {}, message: {}",
                judge.getSubmitId(), judge.getJudgeTaskId(), message);
        pushTimeoutEvent(judge, message);
    }

    private boolean hasActiveOutbox(String judgeTaskId) {
        String normalizedJudgeTaskId = StrUtil.trimToNull(judgeTaskId);
        if (normalizedJudgeTaskId == null) {
            return false;
        }
        Long count = outboxMapper.selectCount(new LambdaQueryWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getJudgeTaskId, normalizedJudgeTaskId)
                .in(JudgeTaskOutbox::getStatus,
                        JudgeTaskOutboxStatusConstant.PENDING,
                        JudgeTaskOutboxStatusConstant.PROCESSING,
                        JudgeTaskOutboxStatusConstant.FAILED)
                .apply("retry_count < max_retry_count"));
        return count != null && count > 0;
    }

    private void pushTimeoutEvent(Judge judge, String message) {
        JudgeResultEventRequest event = new JudgeResultEventRequest();
        event.setEventType(EVENT_JUDGE_FAILED);
        event.setSubmissionId(judge.getSubmitId());
        event.setJudgeTaskId(judge.getJudgeTaskId());
        event.setStatus(SubmissionStatusConstant.SYSTEM_ERROR);
        event.setStatusText(SubmissionStatusConstant.toText(SubmissionStatusConstant.SYSTEM_ERROR));
        event.setTotalCase(defaultZero(judge.getTotalCase()));
        event.setJudgedCase(defaultZero(judge.getJudgedCase()));
        event.setCurrentCase(defaultZero(judge.getCurrentCase()));
        event.setScore(0);
        event.setMessage(message);
        event.setEventTime(OffsetDateTime.now());
        messagingTemplate.convertAndSend(PROGRESS_TOPIC_PREFIX + judge.getSubmitId() + PROGRESS_TOPIC_SUFFIX, event);
    }

    private int batchSize() {
        Integer value = submissionProperties.getJudgeTimeout().getBatchSize();
        return value == null || value <= 0 ? DEFAULT_BATCH_SIZE : value;
    }

    private long pendingTimeoutSeconds() {
        Long value = submissionProperties.getJudgeTimeout().getPendingTimeoutSeconds();
        return value == null || value <= 0 ? DEFAULT_PENDING_TIMEOUT_SECONDS : value;
    }

    private long activeTimeoutSeconds() {
        Long value = submissionProperties.getJudgeTimeout().getActiveTimeoutSeconds();
        return value == null || value <= 0 ? DEFAULT_ACTIVE_TIMEOUT_SECONDS : value;
    }

    private Integer defaultZero(Integer value) {
        return value == null ? 0 : value;
    }
}
