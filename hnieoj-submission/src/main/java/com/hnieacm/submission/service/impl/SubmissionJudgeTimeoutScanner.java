package com.hnieacm.submission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.submission.constant.JudgeTaskOutboxStatusConstant;
import com.hnieacm.submission.constant.SubmissionStatusConstant;
import com.hnieacm.submission.dto.JudgeResultEventRequest;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeTaskOutbox;
import com.hnieacm.submission.entity.RejudgeTaskDetail;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.mapper.JudgeTaskOutboxMapper;
import com.hnieacm.submission.mapper.RejudgeTaskDetailMapper;
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
    private static final long DEFAULT_SENT_PENDING_WARN_SECONDS = 3600L;

    private final JudgeMapper judgeMapper;
    private final JudgeTaskOutboxMapper outboxMapper;
    private final RejudgeTaskDetailMapper rejudgeTaskDetailMapper;
    private final SubmissionProperties submissionProperties;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * @MethodName scanTimeoutSubmissions
     * @Description 扫描长时间停留在判题中状态的提交
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Scheduled(fixedDelayString = "${hnieoj.submission.judge-timeout.scan-interval-ms:60000}")
    public void scanTimeoutSubmissions() {
        if (!Boolean.TRUE.equals(submissionProperties.getJudgeTimeout().getEnabled())) {
            return;
        }
        scanStatus(SubmissionStatusConstant.PENDING, pendingTimeoutSeconds(), "Judge task pending timeout");
        scanStatus(SubmissionStatusConstant.COMPILING, activeTimeoutSeconds(), "Judge task compiling timeout");
        scanStatus(SubmissionStatusConstant.RUNNING, activeTimeoutSeconds(), "Judge task running timeout");
    }

    /**
     * @MethodName scanStatus
     * @Param status
     * @Param timeoutSeconds
     * @Param message
     * @Description 扫描指定状态下已经超时的提交
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
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

    /**
     * @MethodName handleTimeoutJudge
     * @Param judge
     * @Param expectedStatus
     * @Param message
     * @Description 处理单条超时提交
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void handleTimeoutJudge(Judge judge, Integer expectedStatus, String message) {
        if (expectedStatus != null && expectedStatus == SubmissionStatusConstant.PENDING
                && hasSentOutbox(judge.getJudgeTaskId())) {
            warnSentPendingIfNeeded(judge);
            return;
        }
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
        freezeRejudgeTaskDetailByTimeout(judge);
        pushTimeoutEvent(judge, message);
    }

    /**
     * @MethodName freezeRejudgeTaskDetailByTimeout
     * @Param judge
     * @Description 将超时产生的系统错误固化到重判明细
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void freezeRejudgeTaskDetailByTimeout(Judge judge) {
        String judgeTaskId = StrUtil.trimToNull(judge.getJudgeTaskId());
        if (judgeTaskId == null) {
            return;
        }
        rejudgeTaskDetailMapper.update(null, new LambdaUpdateWrapper<RejudgeTaskDetail>()
                .eq(RejudgeTaskDetail::getJudgeId, judge.getId())
                .eq(RejudgeTaskDetail::getJudgeTaskId, judgeTaskId)
                .set(RejudgeTaskDetail::getFinalStatus, SubmissionStatusConstant.SYSTEM_ERROR)
                .set(RejudgeTaskDetail::getFinalScore, 0)
                .set(RejudgeTaskDetail::getFinalTime, judge.getTime())
                .set(RejudgeTaskDetail::getFinalMemory, judge.getMemory())
                .set(RejudgeTaskDetail::getFinishedTime, LocalDateTime.now()));
    }

    /**
     * @MethodName hasActiveOutbox
     * @Param judgeTaskId
     * @Description 判断判题任务是否仍有可重试的 outbox
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
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

    /**
     * @MethodName hasSentOutbox
     * @Param judgeTaskId
     * @Description 判断判题任务是否已成功投递到 RabbitMQ
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private boolean hasSentOutbox(String judgeTaskId) {
        String normalizedJudgeTaskId = StrUtil.trimToNull(judgeTaskId);
        if (normalizedJudgeTaskId == null) {
            return false;
        }
        Long count = outboxMapper.selectCount(new LambdaQueryWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getJudgeTaskId, normalizedJudgeTaskId)
                .eq(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.SENT));
        return count != null && count > 0;
    }

    /**
     * @MethodName warnSentPendingIfNeeded
     * @Param judge
     * @Description 对已投递但长时间未被消费的任务输出告警日志
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void warnSentPendingIfNeeded(Judge judge) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusSeconds(sentPendingWarnSeconds());
        Long count = outboxMapper.selectCount(new LambdaQueryWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getJudgeTaskId, judge.getJudgeTaskId())
                .eq(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.SENT)
                .le(JudgeTaskOutbox::getSentTime, cutoffTime));
        if (count == null || count <= 0) {
            log.info("Submission timeout skipped because task has been sent, submissionId: {}, judgeTaskId: {}",
                    judge.getSubmitId(), judge.getJudgeTaskId());
            return;
        }
        log.warn("Submission pending after RabbitMQ sent, submissionId: {}, judgeTaskId: {}, warnSeconds: {}",
                judge.getSubmitId(), judge.getJudgeTaskId(), sentPendingWarnSeconds());
    }

    /**
     * @MethodName pushTimeoutEvent
     * @Param judge
     * @Param message
     * @Description 推送超时失败事件到提交进度 WebSocket
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
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

    /**
     * @MethodName batchSize
     * @Description 获取超时扫描批次大小
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private int batchSize() {
        Integer value = submissionProperties.getJudgeTimeout().getBatchSize();
        return value == null || value <= 0 ? DEFAULT_BATCH_SIZE : value;
    }

    /**
     * @MethodName pendingTimeoutSeconds
     * @Description 获取待投递状态超时时间
     * @Return @return long
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private long pendingTimeoutSeconds() {
        Long value = submissionProperties.getJudgeTimeout().getPendingTimeoutSeconds();
        return value == null || value <= 0 ? DEFAULT_PENDING_TIMEOUT_SECONDS : value;
    }

    /**
     * @MethodName activeTimeoutSeconds
     * @Description 获取编译或运行状态超时时间
     * @Return @return long
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private long activeTimeoutSeconds() {
        Long value = submissionProperties.getJudgeTimeout().getActiveTimeoutSeconds();
        return value == null || value <= 0 ? DEFAULT_ACTIVE_TIMEOUT_SECONDS : value;
    }

    /**
     * @MethodName sentPendingWarnSeconds
     * @Description 获取已投递未消费告警阈值
     * @Return @return long
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private long sentPendingWarnSeconds() {
        Long value = submissionProperties.getJudgeTimeout().getSentPendingWarnSeconds();
        return value == null || value <= 0 ? DEFAULT_SENT_PENDING_WARN_SECONDS : value;
    }

    /**
     * @MethodName defaultZero
     * @Param value
     * @Description 空值转为 0
     * @Return @return {@link Integer }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private Integer defaultZero(Integer value) {
        return value == null ? 0 : value;
    }
}
