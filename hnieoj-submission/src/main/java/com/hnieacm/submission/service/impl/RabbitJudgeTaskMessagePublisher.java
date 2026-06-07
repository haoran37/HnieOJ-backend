package com.hnieacm.submission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.dto.JudgeTaskMessage;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.properties.JudgeMqProperties;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.submission.constant.JudgeTaskOutboxStatusConstant;
import com.hnieacm.submission.dto.ProblemBasicDto;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeTaskOutbox;
import com.hnieacm.submission.mapper.JudgeTaskOutboxMapper;
import com.hnieacm.submission.properties.SubmissionProperties;
import com.hnieacm.submission.service.JudgeTaskMessagePublisher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/06
 * @Description: RabbitMQ 判题任务消息发布服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitJudgeTaskMessagePublisher implements JudgeTaskMessagePublisher {

    private static final String DEFAULT_JUDGE_MODE = "default";
    private static final int DEFAULT_PROBLEM_TYPE = 0;
    private static final int DEFAULT_TIME_LIMIT = 1000;
    private static final int DEFAULT_MEMORY_LIMIT = 256;
    private static final int DEFAULT_STACK_LIMIT = 128;
    private static final int DEFAULT_IO_SCORE = 100;
    private static final int DEFAULT_DATA_VERSION = 1;
    private static final int DEFAULT_SPJ_TIME_LIMIT = 2000;
    private static final int DEFAULT_SPJ_MEMORY_LIMIT = 256;
    private static final int DEFAULT_SPJ_STACK_LIMIT = 128;
    private static final int DEFAULT_SPJ_OUTPUT_LIMIT = 16777216;
    private static final String DEFAULT_SPJ_PROTOCOL = "testlib";
    private static final String SPJ_JUDGE_MODE = "spj";
    private static final int DEFAULT_RETRY_BATCH_SIZE = 20;
    private static final int DEFAULT_MAX_RETRY_COUNT = 10;
    private static final long DEFAULT_RETRY_BACKOFF_SECONDS = 30L;
    private static final long DEFAULT_PROCESSING_TIMEOUT_SECONDS = 120L;
    private static final int MAX_ERROR_LENGTH = 1000;

    private final RabbitTemplate rabbitTemplate;
    private final JudgeMqProperties properties;
    private final JudgeTaskOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final SubmissionProperties submissionProperties;

    @PostConstruct
    public void initRabbitCallbacks() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            Long outboxId = parseOutboxId(correlationData == null ? null : correlationData.getId());
            if (outboxId == null) {
                log.warn("Rabbit confirm ignored because correlation id is missing, ack: {}, cause: {}", ack, cause);
                return;
            }
            if (ack) {
                markSent(outboxId);
                return;
            }
            markFailed(outboxId, new IllegalStateException(defaultString(cause, "RabbitMQ publisher confirm nack")));
        });
        rabbitTemplate.setReturnsCallback(returned -> {
            Long outboxId = parseOutboxId(returned.getMessage().getMessageProperties().getCorrelationId());
            if (outboxId == null) {
                log.warn("Rabbit returned message ignored because correlation id is missing, replyCode: {}, replyText: {}",
                        returned.getReplyCode(), returned.getReplyText());
                return;
            }
            markFailed(outboxId, new IllegalStateException("RabbitMQ returned message, replyCode: "
                    + returned.getReplyCode() + ", replyText: " + returned.getReplyText()));
        });
    }

    @Override
    public void publishAfterCommit(Judge judge, ProblemBasicDto problem) {
        if (judge == null) {
            return;
        }
        JudgeTaskMessage message = buildMessage(judge, problem);
        JudgeTaskOutbox outbox = createOutbox(message);
        outboxMapper.insert(outbox);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishOutbox(outbox.getId());
                }
            });
            return;
        }
        publishOutbox(outbox.getId());
    }

    @Override
    public void retryOutbox(Long outboxId) {
        if (outboxId == null) {
            return;
        }
        outboxMapper.update(null, new LambdaUpdateWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getId, outboxId)
                .ne(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.SENT)
                .set(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.FAILED)
                .set(JudgeTaskOutbox::getRetryCount, 0)
                .set(JudgeTaskOutbox::getNextRetryTime, LocalDateTime.now())
                .set(JudgeTaskOutbox::getLastError, null));
        publishOutbox(outboxId);
    }

    @Scheduled(fixedDelayString = "${hnieoj.submission.judge-outbox.retry-interval-ms:10000}")
    public void retryPendingOutbox() {
        List<JudgeTaskOutbox> candidates = queryRetryCandidates();
        if (candidates.isEmpty()) {
            return;
        }
        for (JudgeTaskOutbox outbox : candidates) {
            publishOutbox(outbox.getId());
        }
    }

    private void publishOutbox(Long outboxId) {
        if (outboxId == null) {
            return;
        }
        JudgeTaskOutbox outbox = outboxMapper.selectById(outboxId);
        if (outbox == null || JudgeTaskOutboxStatusConstant.SENT.equals(outbox.getStatus())) {
            return;
        }
        if (!markProcessing(outbox)) {
            return;
        }
        try {
            JudgeTaskMessage message = objectMapper.readValue(outbox.getPayload(), JudgeTaskMessage.class);
            rabbitTemplate.convertAndSend(outbox.getExchangeName(), outbox.getRoutingKey(), message, item -> {
                item.getMessageProperties().setMessageId(message.getMessageId());
                item.getMessageProperties().setCorrelationId(String.valueOf(outbox.getId()));
                item.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return item;
            }, new CorrelationData(String.valueOf(outbox.getId())));
            log.info("Judge task message sent to RabbitTemplate, submissionId: {}, judgeId: {}, messageId: {}",
                    message.getSubmissionId(), message.getJudgeId(), message.getMessageId());
        } catch (Exception e) {
            markFailed(outbox, e);
            log.error("Publish judge task message failed, outboxId: {}, submissionId: {}, messageId: {}",
                    outbox.getId(), outbox.getSubmissionId(), outbox.getMessageId(), e);
        }
    }

    private JudgeTaskOutbox createOutbox(JudgeTaskMessage message) {
        JudgeTaskOutbox outbox = new JudgeTaskOutbox();
        outbox.setMessageId(message.getMessageId());
        outbox.setJudgeTaskId(message.getJudgeTaskId());
        outbox.setSubmissionId(message.getSubmissionId());
        outbox.setExchangeName(properties.getExchange());
        outbox.setRoutingKey(properties.getRoutingKey());
        outbox.setStatus(JudgeTaskOutboxStatusConstant.PENDING);
        outbox.setRetryCount(0);
        outbox.setMaxRetryCount(maxRetryCount());
        outbox.setNextRetryTime(LocalDateTime.now());
        try {
            outbox.setPayload(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "创建判题任务失败");
        }
        return outbox;
    }

    private List<JudgeTaskOutbox> queryRetryCandidates() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleProcessingTime = now.minusSeconds(processingTimeoutSeconds());
        return outboxMapper.selectList(new LambdaQueryWrapper<JudgeTaskOutbox>()
                .lt(JudgeTaskOutbox::getRetryCount, maxRetryCount())
                .and(wrapper -> wrapper
                        .and(item -> item.in(JudgeTaskOutbox::getStatus,
                                        JudgeTaskOutboxStatusConstant.PENDING,
                                        JudgeTaskOutboxStatusConstant.FAILED)
                                .le(JudgeTaskOutbox::getNextRetryTime, now))
                        .or(item -> item.eq(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.PROCESSING)
                                .le(JudgeTaskOutbox::getGmtModified, staleProcessingTime)))
                .orderByAsc(JudgeTaskOutbox::getNextRetryTime)
                .orderByAsc(JudgeTaskOutbox::getId)
                .last("limit " + retryBatchSize()));
    }

    private boolean markProcessing(JudgeTaskOutbox outbox) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleProcessingTime = now.minusSeconds(processingTimeoutSeconds());
        int updated = outboxMapper.update(null, new LambdaUpdateWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getId, outbox.getId())
                .lt(JudgeTaskOutbox::getRetryCount, maxRetryCount())
                .and(wrapper -> wrapper
                        .and(item -> item.in(JudgeTaskOutbox::getStatus,
                                        JudgeTaskOutboxStatusConstant.PENDING,
                                        JudgeTaskOutboxStatusConstant.FAILED)
                                .le(JudgeTaskOutbox::getNextRetryTime, now))
                        .or(item -> item.eq(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.PROCESSING)
                                .le(JudgeTaskOutbox::getGmtModified, staleProcessingTime)))
                .set(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.PROCESSING));
        return updated > 0;
    }

    private void markSent(Long outboxId) {
        outboxMapper.update(null, new LambdaUpdateWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getId, outboxId)
                .eq(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.PROCESSING)
                .set(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.SENT)
                .set(JudgeTaskOutbox::getSentTime, LocalDateTime.now())
                .set(JudgeTaskOutbox::getLastError, null)
                .set(JudgeTaskOutbox::getNextRetryTime, null));
    }

    private void markFailed(Long outboxId, Exception e) {
        JudgeTaskOutbox outbox = outboxMapper.selectById(outboxId);
        if (outbox == null) {
            return;
        }
        markFailed(outbox, e);
    }

    private void markFailed(JudgeTaskOutbox outbox, Exception e) {
        int nextRetryCount = defaultInteger(outbox.getRetryCount(), 0) + 1;
        boolean exhausted = nextRetryCount >= maxRetryCount();
        LambdaUpdateWrapper<JudgeTaskOutbox> wrapper = new LambdaUpdateWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getId, outbox.getId())
                .eq(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.PROCESSING)
                .set(JudgeTaskOutbox::getRetryCount, nextRetryCount)
                .set(JudgeTaskOutbox::getStatus, exhausted
                        ? JudgeTaskOutboxStatusConstant.EXHAUSTED : JudgeTaskOutboxStatusConstant.FAILED)
                .set(JudgeTaskOutbox::getLastError, truncateError(e.getMessage()));
        if (exhausted) {
            wrapper.set(JudgeTaskOutbox::getNextRetryTime, null);
        } else {
            wrapper.set(JudgeTaskOutbox::getNextRetryTime,
                    LocalDateTime.now().plusSeconds(retryBackoffSeconds()));
        }
        outboxMapper.update(null, wrapper);
    }

    private JudgeTaskMessage buildMessage(Judge judge, ProblemBasicDto problem) {
        JudgeTaskMessage message = new JudgeTaskMessage();
        String messageId = UUID.randomUUID().toString().replace("-", "");
        message.setMessageId(messageId);
        message.setJudgeTaskId(defaultString(judge.getJudgeTaskId(), messageId));
        message.setJudgeId(judge.getId());
        message.setSubmissionId(judge.getSubmitId());
        message.setProblemId(judge.getProblemId());
        message.setProblemCode(judge.getProblemCode());
        message.setUid(judge.getUid());
        message.setLanguage(judge.getLanguage());
        message.setCode(judge.getCode());
        message.setContestId(judge.getCid());
        message.setJudgeMode(defaultString(problem == null ? null : problem.getJudgeMode(), DEFAULT_JUDGE_MODE));
        message.setProblemType(defaultInteger(problem == null ? null : problem.getType(), DEFAULT_PROBLEM_TYPE));
        message.setTimeLimit(defaultInteger(problem == null ? null : problem.getTimeLimit(), DEFAULT_TIME_LIMIT));
        message.setMemoryLimit(defaultInteger(problem == null ? null : problem.getMemoryLimit(), DEFAULT_MEMORY_LIMIT));
        message.setStackLimit(defaultInteger(problem == null ? null : problem.getStackLimit(), DEFAULT_STACK_LIMIT));
        message.setChecker(buildChecker(message.getJudgeMode(), problem));
        message.setIoScore(defaultInteger(problem == null ? null : problem.getIoScore(), DEFAULT_IO_SCORE));
        message.setIsRemoveEndBlank(problem == null || !Boolean.FALSE.equals(problem.getIsRemoveEndBlank()));
        message.setDataVersion(defaultInteger(problem == null ? null : problem.getDataVersion(), DEFAULT_DATA_VERSION));
        message.setCreatedAt(OffsetDateTime.now(ZoneId.systemDefault()).toString());
        message.setCreatedAtMillis(System.currentTimeMillis());
        return message;
    }

    private JudgeTaskMessage.JudgeAsset buildChecker(String judgeMode, ProblemBasicDto problem) {
        if (!SPJ_JUDGE_MODE.equalsIgnoreCase(defaultString(judgeMode, DEFAULT_JUDGE_MODE)) || problem == null) {
            return null;
        }
        JudgeTaskMessage.JudgeAsset checker = new JudgeTaskMessage.JudgeAsset();
        checker.setLanguage(problem.getSpjLanguage());
        checker.setSource(problem.getSpjCode());
        checker.setArtifactFileId(null);
        checker.setTimeLimit(defaultInteger(problem.getSpjTimeLimit(), DEFAULT_SPJ_TIME_LIMIT));
        checker.setMemoryLimit(defaultInteger(problem.getSpjMemoryLimit(), DEFAULT_SPJ_MEMORY_LIMIT));
        checker.setStackLimit(defaultInteger(problem.getSpjStackLimit(), DEFAULT_SPJ_STACK_LIMIT));
        checker.setOutputLimit(defaultInteger(problem.getSpjOutputLimit(), DEFAULT_SPJ_OUTPUT_LIMIT));
        checker.setProtocol(defaultString(problem.getSpjProtocol(), DEFAULT_SPJ_PROTOCOL));
        return checker;
    }

    private Integer defaultInteger(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private int retryBatchSize() {
        Integer value = submissionProperties.getJudgeOutbox().getRetryBatchSize();
        return value == null || value <= 0 ? DEFAULT_RETRY_BATCH_SIZE : value;
    }

    private int maxRetryCount() {
        Integer value = submissionProperties.getJudgeOutbox().getMaxRetryCount();
        return value == null || value <= 0 ? DEFAULT_MAX_RETRY_COUNT : value;
    }

    private long retryBackoffSeconds() {
        Long value = submissionProperties.getJudgeOutbox().getRetryBackoffSeconds();
        return value == null || value <= 0 ? DEFAULT_RETRY_BACKOFF_SECONDS : value;
    }

    private long processingTimeoutSeconds() {
        Long value = submissionProperties.getJudgeOutbox().getProcessingTimeoutSeconds();
        return value == null || value <= 0 ? DEFAULT_PROCESSING_TIMEOUT_SECONDS : value;
    }

    private String truncateError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private Long parseOutboxId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
