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

import java.nio.charset.StandardCharsets;
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
    private static final String DEFAULT_RESULT_PROTOCOL = "hnieoj-result-json-v1";
    private static final String DEFAULT_SPJ_ARGUMENT_TEMPLATE = "{input} {expected} {actual} {result}";
    private static final int DEFAULT_SCHEMA_VERSION = 2;
    private static final int DEFAULT_INTERACTOR_TIME_LIMIT = 5000;
    private static final int DEFAULT_INTERACTOR_MEMORY_LIMIT = 256;
    private static final int DEFAULT_INTERACTOR_STACK_LIMIT = 128;
    private static final int DEFAULT_INTERACTOR_OUTPUT_LIMIT = 16777216;
    private static final String DEFAULT_INTERACTOR_ARGUMENT_TEMPLATE = "{input} {expected} {result}";
    private static final String DEFAULT_INTERACTION_PROTOCOL = "stdio";
    private static final String DEFAULT_INTERACTION_WIRING = "bidirectional-stdio";
    private static final String DEFAULT_INTERACTION_SCORE_MODE = "interactor";
    private static final String SPJ_JUDGE_MODE = "spj";
    private static final String INTERACTIVE_JUDGE_MODE = "interactive";
    private static final int DEFAULT_RETRY_BATCH_SIZE = 20;
    private static final int DEFAULT_MAX_RETRY_COUNT = 10;
    private static final long DEFAULT_RETRY_BACKOFF_SECONDS = 30L;
    private static final long DEFAULT_PROCESSING_TIMEOUT_SECONDS = 120L;
    private static final int MAX_ERROR_LENGTH = 1000;
    private static final int DEFAULT_MAX_CODE_BYTES = 65536;
    private static final int DEFAULT_MAX_CHECKER_BYTES = 262144;
    private static final int DEFAULT_MAX_INTERACTOR_BYTES = 262144;
    private static final int DEFAULT_MAX_MESSAGE_PAYLOAD_BYTES = 1048576;

    private final RabbitTemplate rabbitTemplate;
    private final JudgeMqProperties properties;
    private final JudgeTaskOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final SubmissionProperties submissionProperties;

    /**
     * @MethodName initRabbitCallbacks
     * @Description 初始化 RabbitMQ confirm 和 return 回调
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @PostConstruct
    public void initRabbitCallbacks() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            PublishCorrelation correlation = parseCorrelation(correlationData == null ? null : correlationData.getId());
            if (correlation == null) {
                log.warn("Rabbit confirm ignored because correlation id is missing, ack: {}, cause: {}", ack, cause);
                return;
            }
            if (ack) {
                markSent(correlation);
                return;
            }
            markFailed(correlation, new IllegalStateException(defaultString(cause, "RabbitMQ publisher confirm nack")));
        });
        rabbitTemplate.setReturnsCallback(returned -> {
            PublishCorrelation correlation = parseCorrelation(returned.getMessage().getMessageProperties().getCorrelationId());
            if (correlation == null) {
                log.warn("Rabbit returned message ignored because correlation id is missing, replyCode: {}, replyText: {}",
                        returned.getReplyCode(), returned.getReplyText());
                return;
            }
            markFailed(correlation, new IllegalStateException("RabbitMQ returned message, replyCode: "
                    + returned.getReplyCode() + ", replyText: " + returned.getReplyText()));
        });
    }

    /**
     * @MethodName publishAfterCommit
     * @Param judge
     * @Param problem
     * @Description 创建 outbox 并在事务提交后投递判题任务
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
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

    /**
     * @MethodName retryOutbox
     * @Param outboxId
     * @Description 手动重试指定 outbox
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
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

    /**
     * @MethodName retryPendingOutbox
     * @Description 定时扫描并重试可投递的 outbox
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
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

    /**
     * @MethodName publishOutbox
     * @Param outboxId
     * @Description 将指定 outbox 投递到 RabbitMQ
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void publishOutbox(Long outboxId) {
        if (outboxId == null) {
            return;
        }
        JudgeTaskOutbox outbox = outboxMapper.selectById(outboxId);
        if (outbox == null || JudgeTaskOutboxStatusConstant.SENT.equals(outbox.getStatus())) {
            return;
        }
        JudgeTaskOutbox processingOutbox = markProcessing(outbox);
        if (processingOutbox == null) {
            return;
        }
        try {
            JudgeTaskMessage message = objectMapper.readValue(processingOutbox.getPayload(), JudgeTaskMessage.class);
            String correlationId = buildCorrelationId(processingOutbox);
            rabbitTemplate.convertAndSend(processingOutbox.getExchangeName(), processingOutbox.getRoutingKey(), message, item -> {
                item.getMessageProperties().setMessageId(message.getMessageId());
                item.getMessageProperties().setCorrelationId(correlationId);
                item.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return item;
            }, new CorrelationData(correlationId));
            log.info("Judge task message sent to RabbitTemplate, submissionId: {}, judgeId: {}, messageId: {}",
                    message.getSubmissionId(), message.getJudgeId(), message.getMessageId());
        } catch (Exception e) {
            markFailed(processingOutbox, e);
            log.error("Publish judge task message failed, outboxId: {}, submissionId: {}, messageId: {}",
                    outbox.getId(), outbox.getSubmissionId(), outbox.getMessageId(), e);
        }
    }

    /**
     * @MethodName createOutbox
     * @Param message
     * @Description 创建判题任务 outbox 记录
     * @Return @return {@link JudgeTaskOutbox }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private JudgeTaskOutbox createOutbox(JudgeTaskMessage message) {
        JudgeTaskOutbox outbox = new JudgeTaskOutbox();
        outbox.setMessageId(message.getMessageId());
        outbox.setJudgeTaskId(message.getJudgeTaskId());
        outbox.setSubmissionId(message.getSubmissionId());
        outbox.setExchangeName(properties.getExchange());
        outbox.setRoutingKey(routingKey(message.getJudgeMode()));
        outbox.setStatus(JudgeTaskOutboxStatusConstant.PENDING);
        outbox.setRetryCount(0);
        outbox.setPublishAttempt(0);
        outbox.setMaxRetryCount(maxRetryCount());
        outbox.setNextRetryTime(LocalDateTime.now());
        try {
            validateTaskPayload(message);
            String payload = objectMapper.writeValueAsString(message);
            validateBytes(payload, maxMessagePayloadBytes(), "判题任务消息不能超过 " + maxMessagePayloadBytes() + " 字节");
            outbox.setPayload(payload);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "创建判题任务失败");
        }
        return outbox;
    }

    /**
     * @MethodName queryRetryCandidates
     * @Description 查询本轮可以重试的 outbox
     * @Return @return {@link List }<{@link JudgeTaskOutbox }>
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
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

    /**
     * @MethodName markProcessing
     * @Param outbox
     * @Description 抢占 outbox 并递增投递 attempt
     * @Return @return {@link JudgeTaskOutbox }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private JudgeTaskOutbox markProcessing(JudgeTaskOutbox outbox) {
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
                .set(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.PROCESSING)
                .setSql("publish_attempt = publish_attempt + 1"));
        if (updated <= 0) {
            return null;
        }
        return outboxMapper.selectById(outbox.getId());
    }

    /**
     * @MethodName markSent
     * @Param correlation
     * @Description 按投递 attempt 标记 outbox 已发送
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void markSent(PublishCorrelation correlation) {
        outboxMapper.update(null, new LambdaUpdateWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getId, correlation.outboxId())
                .eq(JudgeTaskOutbox::getPublishAttempt, correlation.publishAttempt())
                .eq(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.PROCESSING)
                .set(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.SENT)
                .set(JudgeTaskOutbox::getSentTime, LocalDateTime.now())
                .set(JudgeTaskOutbox::getLastError, null)
                .set(JudgeTaskOutbox::getNextRetryTime, null));
    }

    /**
     * @MethodName markFailed
     * @Param correlation
     * @Param e
     * @Description 根据 RabbitMQ 回调标记 outbox 投递失败
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void markFailed(PublishCorrelation correlation, Exception e) {
        JudgeTaskOutbox outbox = outboxMapper.selectById(correlation.outboxId());
        if (outbox == null) {
            return;
        }
        if (!Integer.valueOf(correlation.publishAttempt()).equals(defaultInteger(outbox.getPublishAttempt(), 0))) {
            log.info("Rabbit callback ignored because publish attempt mismatch, outboxId: {}, callbackAttempt: {}, currentAttempt: {}",
                    correlation.outboxId(), correlation.publishAttempt(), outbox.getPublishAttempt());
            return;
        }
        markFailed(outbox, e);
    }

    /**
     * @MethodName markFailed
     * @Param outbox
     * @Param e
     * @Description 根据本地异常更新 outbox 失败状态和下次重试时间
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void markFailed(JudgeTaskOutbox outbox, Exception e) {
        int nextRetryCount = defaultInteger(outbox.getRetryCount(), 0) + 1;
        boolean exhausted = nextRetryCount >= maxRetryCount();
        LambdaUpdateWrapper<JudgeTaskOutbox> wrapper = new LambdaUpdateWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getId, outbox.getId())
                .eq(JudgeTaskOutbox::getPublishAttempt, defaultInteger(outbox.getPublishAttempt(), 0))
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

    /**
     * @MethodName buildMessage
     * @Param judge
     * @Param problem
     * @Description 构造发送给判题节点的任务消息
     * @Return @return {@link JudgeTaskMessage }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private JudgeTaskMessage buildMessage(Judge judge, ProblemBasicDto problem) {
        JudgeTaskMessage message = new JudgeTaskMessage();
        String messageId = UUID.randomUUID().toString().replace("-", "");
        message.setMessageId(messageId);
        message.setSchemaVersion(DEFAULT_SCHEMA_VERSION);
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
        message.setInteractor(buildInteractor(message.getJudgeMode(), problem));
        message.setInteraction(buildInteraction(message.getJudgeMode(), problem));
        message.setIoScore(defaultInteger(problem == null ? null : problem.getIoScore(), DEFAULT_IO_SCORE));
        message.setIsRemoveEndBlank(problem == null || !Boolean.FALSE.equals(problem.getIsRemoveEndBlank()));
        message.setDataVersion(defaultInteger(problem == null ? null : problem.getDataVersion(), DEFAULT_DATA_VERSION));
        message.setCreatedAt(OffsetDateTime.now(ZoneId.systemDefault()).toString());
        message.setCreatedAtMillis(System.currentTimeMillis());
        return message;
    }

    /**
     * @MethodName buildChecker
     * @Param judgeMode
     * @Param problem
     * @Description 构造 SPJ checker 配置
     * @Return @return {@link JudgeTaskMessage.JudgeAsset }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
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
        checker.setProtocol(defaultString(problem.getSpjProtocol(), DEFAULT_RESULT_PROTOCOL));
        checker.setArgumentTemplate(DEFAULT_SPJ_ARGUMENT_TEMPLATE);
        return checker;
    }

    /**
     * @MethodName buildInteractor
     * @Param judgeMode
     * @Param problem
     * @Description 构造交互题 interactor 配置
     * @Return @return {@link JudgeTaskMessage.JudgeAsset }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private JudgeTaskMessage.JudgeAsset buildInteractor(String judgeMode, ProblemBasicDto problem) {
        if (!INTERACTIVE_JUDGE_MODE.equalsIgnoreCase(defaultString(judgeMode, DEFAULT_JUDGE_MODE)) || problem == null) {
            return null;
        }
        JudgeTaskMessage.JudgeAsset interactor = new JudgeTaskMessage.JudgeAsset();
        interactor.setLanguage(problem.getInteractorLanguage());
        interactor.setSource(problem.getInteractorCode());
        interactor.setArtifactFileId(null);
        interactor.setTimeLimit(defaultInteger(problem.getInteractorTimeLimit(), DEFAULT_INTERACTOR_TIME_LIMIT));
        interactor.setMemoryLimit(defaultInteger(problem.getInteractorMemoryLimit(), DEFAULT_INTERACTOR_MEMORY_LIMIT));
        interactor.setStackLimit(defaultInteger(problem.getInteractorStackLimit(), DEFAULT_INTERACTOR_STACK_LIMIT));
        interactor.setOutputLimit(defaultInteger(problem.getInteractorOutputLimit(), DEFAULT_INTERACTOR_OUTPUT_LIMIT));
        interactor.setProtocol(defaultString(problem.getInteractorProtocol(), DEFAULT_RESULT_PROTOCOL));
        interactor.setArgumentTemplate(DEFAULT_INTERACTOR_ARGUMENT_TEMPLATE);
        return interactor;
    }

    /**
     * @MethodName buildInteraction
     * @Param judgeMode
     * @Param problem
     * @Description 构造交互题进程通信配置
     * @Return @return {@link JudgeTaskMessage.InteractionConfig }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private JudgeTaskMessage.InteractionConfig buildInteraction(String judgeMode, ProblemBasicDto problem) {
        if (!INTERACTIVE_JUDGE_MODE.equalsIgnoreCase(defaultString(judgeMode, DEFAULT_JUDGE_MODE)) || problem == null) {
            return null;
        }
        JudgeTaskMessage.InteractionConfig interaction = new JudgeTaskMessage.InteractionConfig();
        interaction.setProtocol(DEFAULT_INTERACTION_PROTOCOL);
        interaction.setWiring(DEFAULT_INTERACTION_WIRING);
        interaction.setScoreMode(DEFAULT_INTERACTION_SCORE_MODE);
        return interaction;
    }

    /**
     * @MethodName routingKey
     * @Param judgeMode
     * @Description 按判题模式选择 RabbitMQ routing key
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private String routingKey(String judgeMode) {
        String normalizedJudgeMode = defaultString(judgeMode, DEFAULT_JUDGE_MODE);
        if (SPJ_JUDGE_MODE.equalsIgnoreCase(normalizedJudgeMode)) {
            return defaultString(properties.getSpjRoutingKey(), properties.getRoutingKey());
        }
        if (INTERACTIVE_JUDGE_MODE.equalsIgnoreCase(normalizedJudgeMode)) {
            return defaultString(properties.getInteractiveRoutingKey(), properties.getRoutingKey());
        }
        return properties.getRoutingKey();
    }

    /**
     * @MethodName defaultInteger
     * @Param value
     * @Param defaultValue
     * @Description 获取整数默认值
     * @Return @return {@link Integer }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private Integer defaultInteger(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * @MethodName defaultString
     * @Param value
     * @Param defaultValue
     * @Description 获取字符串默认值
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * @MethodName retryBatchSize
     * @Description 获取 outbox 重试批次大小
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private int retryBatchSize() {
        Integer value = submissionProperties.getJudgeOutbox().getRetryBatchSize();
        return value == null || value <= 0 ? DEFAULT_RETRY_BATCH_SIZE : value;
    }

    /**
     * @MethodName maxRetryCount
     * @Description 获取 outbox 最大重试次数
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private int maxRetryCount() {
        Integer value = submissionProperties.getJudgeOutbox().getMaxRetryCount();
        return value == null || value <= 0 ? DEFAULT_MAX_RETRY_COUNT : value;
    }

    /**
     * @MethodName retryBackoffSeconds
     * @Description 获取 outbox 重试退避时间
     * @Return @return long
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private long retryBackoffSeconds() {
        Long value = submissionProperties.getJudgeOutbox().getRetryBackoffSeconds();
        return value == null || value <= 0 ? DEFAULT_RETRY_BACKOFF_SECONDS : value;
    }

    /**
     * @MethodName processingTimeoutSeconds
     * @Description 获取 processing 状态抢占超时时间
     * @Return @return long
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private long processingTimeoutSeconds() {
        Long value = submissionProperties.getJudgeOutbox().getProcessingTimeoutSeconds();
        return value == null || value <= 0 ? DEFAULT_PROCESSING_TIMEOUT_SECONDS : value;
    }

    /**
     * @MethodName validateTaskPayload
     * @Param message
     * @Description 校验判题任务 payload 中源码字段大小
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void validateTaskPayload(JudgeTaskMessage message) {
        validateBytes(message.getCode(), maxCodeBytes(), "提交代码不能超过 " + maxCodeBytes() + " 字节");
        if (message.getChecker() != null) {
            validateBytes(message.getChecker().getSource(), maxCheckerBytes(),
                    "SPJ checker 源码不能超过 " + maxCheckerBytes() + " 字节");
        }
        if (message.getInteractor() != null) {
            validateBytes(message.getInteractor().getSource(), maxInteractorBytes(),
                    "交互题 interactor 源码不能超过 " + maxInteractorBytes() + " 字节");
        }
    }

    /**
     * @MethodName validateBytes
     * @Param value
     * @Param maxBytes
     * @Param message
     * @Description 校验字符串 UTF-8 字节数上限
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void validateBytes(String value, int maxBytes, String message) {
        if (value == null) {
            return;
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new BizException(ResultCode.BAD_REQUEST, message);
        }
    }

    /**
     * @MethodName maxCodeBytes
     * @Description 获取提交源码最大字节数
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private int maxCodeBytes() {
        Integer value = submissionProperties.getMaxCodeBytes();
        return value == null || value <= 0 ? DEFAULT_MAX_CODE_BYTES : value;
    }

    /**
     * @MethodName maxCheckerBytes
     * @Description 获取 SPJ checker 源码最大字节数
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private int maxCheckerBytes() {
        Integer value = submissionProperties.getMaxCheckerBytes();
        return value == null || value <= 0 ? DEFAULT_MAX_CHECKER_BYTES : value;
    }

    /**
     * @MethodName maxInteractorBytes
     * @Description 获取交互题 interactor 源码最大字节数
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private int maxInteractorBytes() {
        Integer value = submissionProperties.getMaxInteractorBytes();
        return value == null || value <= 0 ? DEFAULT_MAX_INTERACTOR_BYTES : value;
    }

    /**
     * @MethodName maxMessagePayloadBytes
     * @Description 获取判题任务消息最大字节数
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private int maxMessagePayloadBytes() {
        Integer value = submissionProperties.getMaxMessagePayloadBytes();
        return value == null || value <= 0 ? DEFAULT_MAX_MESSAGE_PAYLOAD_BYTES : value;
    }

    /**
     * @MethodName truncateError
     * @Param message
     * @Description 截断 outbox 错误信息
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private String truncateError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    /**
     * @MethodName buildCorrelationId
     * @Param outbox
     * @Description 构造携带 outboxId 和 attempt 的 RabbitMQ correlation id
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private String buildCorrelationId(JudgeTaskOutbox outbox) {
        return outbox.getId() + ":" + defaultInteger(outbox.getPublishAttempt(), 0);
    }

    /**
     * @MethodName parseCorrelation
     * @Param value
     * @Description 解析 RabbitMQ correlation id
     * @Return @return {@link PublishCorrelation }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private PublishCorrelation parseCorrelation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] segments = value.split(":");
        if (segments.length != 2) {
            return null;
        }
        try {
            return new PublishCorrelation(Long.parseLong(segments[0]), Integer.parseInt(segments[1]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record PublishCorrelation(Long outboxId, Integer publishAttempt) {
    }
}
