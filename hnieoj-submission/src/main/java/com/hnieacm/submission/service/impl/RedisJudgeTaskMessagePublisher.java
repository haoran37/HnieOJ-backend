package com.hnieacm.submission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.dto.JudgeTaskMessage;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.properties.JudgeStreamProperties;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.submission.constant.JudgeTaskExecutionStatusConstant;
import com.hnieacm.submission.constant.JudgeTaskOutboxStatusConstant;
import com.hnieacm.submission.dto.ProblemBasicDto;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeTaskExecution;
import com.hnieacm.submission.entity.JudgeTaskOutbox;
import com.hnieacm.submission.mapper.JudgeTaskExecutionMapper;
import com.hnieacm.submission.mapper.JudgeTaskOutboxMapper;
import com.hnieacm.submission.properties.SubmissionProperties;
import com.hnieacm.submission.service.JudgeTaskMessagePublisher;
import com.hnieacm.submission.service.JudgeTaskStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: Redis Streams 判题任务发布服务：同事务 Outbox + 提交后 XADD + 失败重试
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisJudgeTaskMessagePublisher implements JudgeTaskMessagePublisher {

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
    private static final Set<String> FIXED_JUDGE_MODES = Set.of(DEFAULT_JUDGE_MODE, SPJ_JUDGE_MODE,
            INTERACTIVE_JUDGE_MODE);
    private static final String LEGACY_TRANSPORT_MARK = "redis-streams";
    private static final int DEFAULT_RETRY_BATCH_SIZE = 20;
    private static final int DEFAULT_MAX_RETRY_COUNT = 10;
    private static final long DEFAULT_RETRY_BACKOFF_SECONDS = 30L;
    private static final long DEFAULT_PROCESSING_TIMEOUT_SECONDS = 120L;
    private static final int MAX_ERROR_LENGTH = 1000;
    private static final int DEFAULT_MAX_CODE_BYTES = 65536;
    private static final int DEFAULT_MAX_CHECKER_BYTES = 262144;
    private static final int DEFAULT_MAX_INTERACTOR_BYTES = 262144;
    private static final int DEFAULT_MAX_MESSAGE_PAYLOAD_BYTES = 1048576;
    private static final int DEFAULT_MAX_ATTEMPT_COUNT = 3;

    private final JudgeTaskStreamService streamService;
    private final JudgeStreamProperties streamProperties;
    private final JudgeTaskOutboxMapper outboxMapper;
    private final JudgeTaskExecutionMapper executionMapper;
    private final ObjectMapper objectMapper;
    private final SubmissionProperties submissionProperties;

    /**
     * @MethodName publishAfterCommit
     * @Param judge
     * @Param problem
     * @Description 同事务写入 Outbox 与执行租约，提交后 XADD 投递判题任务
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Override
    public void publishAfterCommit(Judge judge, ProblemBasicDto problem) {
        if (judge == null) {
            return;
        }
        JudgeTaskMessage message = buildMessage(judge, problem);
        JudgeTaskOutbox outbox = createOutbox(message);
        outboxMapper.insert(outbox);
        upsertExecution(judge, message, outbox);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
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
     * @Date 2026/09/18
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
                .set(JudgeTaskOutbox::getNextRetryTime, LocalDateTime.now().withNano(0).minusSeconds(1))
                .set(JudgeTaskOutbox::getLastError, null));
        publishOutbox(outboxId);
    }

    /**
     * @MethodName retryPendingOutbox
     * @Description 定时扫描并重试可投递的 outbox
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
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
     * @Description 将指定 outbox XADD 到 Redis Stream
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
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
            String streamKey = resolveStreamKey(processingOutbox, message);
            String recordId = streamService.publish(streamKey, processingOutbox.getPayload());
            markSent(processingOutbox, streamKey, recordId);
            log.info("Judge task message sent to Redis Stream, submissionId: {}, judgeId: {}, messageId: {}, streamKey: {}, recordId: {}",
                    message.getSubmissionId(), message.getJudgeId(), message.getMessageId(), streamKey, recordId);
        } catch (Exception e) {
            markFailed(processingOutbox, e);
            log.error("Publish judge task message failed, outboxId: {}, submissionId: {}, messageId: {}",
                    outbox.getId(), outbox.getSubmissionId(), outbox.getMessageId(), e);
        }
    }

    private void markSent(JudgeTaskOutbox outbox, String streamKey, String recordId) {
        outboxMapper.update(null, new LambdaUpdateWrapper<JudgeTaskOutbox>()
                .eq(JudgeTaskOutbox::getId, outbox.getId())
                .eq(JudgeTaskOutbox::getPublishAttempt, defaultInteger(outbox.getPublishAttempt(), 0))
                .eq(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.PROCESSING)
                .set(JudgeTaskOutbox::getStatus, JudgeTaskOutboxStatusConstant.SENT)
                .set(JudgeTaskOutbox::getStreamKey, streamKey)
                .set(JudgeTaskOutbox::getStreamId, recordId)
                .set(JudgeTaskOutbox::getSentTime, LocalDateTime.now())
                .set(JudgeTaskOutbox::getLastError, null)
                .set(JudgeTaskOutbox::getNextRetryTime, null));
        executionMapper.update(null, new LambdaUpdateWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getSubmissionId, outbox.getSubmissionId())
                .eq(JudgeTaskExecution::getJudgeTaskId, outbox.getJudgeTaskId())
                .set(JudgeTaskExecution::getStreamKey, streamKey)
                .set(JudgeTaskExecution::getStreamId, recordId));
    }

    /**
     * @MethodName alignedInitialNextRetryTime
     * @Param now
     * @Description 将首次重试时间对齐到数据库 DATETIME(0) 整秒并回退 1 秒：避免 .800 之类小数
     * 被四舍五入进下一秒，导致刚写入的 outbox 立刻判定为“未到重试时间”而不可投递。
     * @Return @return {@link LocalDateTime }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    static LocalDateTime alignedInitialNextRetryTime(LocalDateTime now) {
        return now.withNano(0).minusSeconds(1);
    }

    private JudgeTaskOutbox createOutbox(JudgeTaskMessage message) {
        JudgeTaskOutbox outbox = new JudgeTaskOutbox();
        outbox.setMessageId(message.getMessageId());
        outbox.setJudgeTaskId(message.getJudgeTaskId());
        outbox.setSubmissionId(message.getSubmissionId());
        String streamKey = streamService.resolveStreamKey(message.getJudgeMode());
        // 遗留列保留写入以兼容 NOT NULL 约束，真实分发定位使用 stream_key / stream_id
        outbox.setExchangeName(LEGACY_TRANSPORT_MARK);
        outbox.setRoutingKey(streamKey);
        outbox.setStreamKey(streamKey);
        outbox.setStatus(JudgeTaskOutboxStatusConstant.PENDING);
        outbox.setRetryCount(0);
        outbox.setPublishAttempt(0);
        outbox.setMaxRetryCount(maxRetryCount());
        outbox.setNextRetryTime(alignedInitialNextRetryTime(LocalDateTime.now()));
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
     * @MethodName upsertExecution
     * @Param judge
     * @Param message
     * @Param outbox
     * @Description 同事务维护执行租约；重判更换 judgeTaskId 时重置执行预算
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    private void upsertExecution(Judge judge, JudgeTaskMessage message, JudgeTaskOutbox outbox) {
        long now = System.currentTimeMillis();
        long deadline = now + executionDeadlineSeconds() * 1000L;
        JudgeTaskExecution execution = executionMapper.selectBySubmissionIdForUpdate(message.getSubmissionId());
        if (execution == null) {
            execution = new JudgeTaskExecution();
            execution.setSubmissionId(message.getSubmissionId());
            execution.setJudgeId(judge.getId());
            execution.setJudgeTaskId(message.getJudgeTaskId());
            execution.setProblemId(judge.getProblemId());
            execution.setProblemCode(judge.getProblemCode());
            execution.setJudgeMode(defaultString(message.getJudgeMode(), DEFAULT_JUDGE_MODE));
            execution.setStreamKey(outbox.getStreamKey());
            execution.setAttemptCount(0);
            execution.setMaxAttemptCount(maxAttemptCount());
            execution.setStatus(JudgeTaskExecutionStatusConstant.QUEUED);
            execution.setExecutionDeadline(deadline);
            executionMapper.insert(execution);
            return;
        }
        executionMapper.update(null, new LambdaUpdateWrapper<JudgeTaskExecution>()
                .eq(JudgeTaskExecution::getId, execution.getId())
                .set(JudgeTaskExecution::getJudgeId, judge.getId())
                .set(JudgeTaskExecution::getJudgeTaskId, message.getJudgeTaskId())
                .set(JudgeTaskExecution::getProblemId, judge.getProblemId())
                .set(JudgeTaskExecution::getProblemCode, judge.getProblemCode())
                .set(JudgeTaskExecution::getJudgeMode, defaultString(message.getJudgeMode(), DEFAULT_JUDGE_MODE))
                .set(JudgeTaskExecution::getStreamKey, outbox.getStreamKey())
                .set(JudgeTaskExecution::getStreamId, null)
                .set(JudgeTaskExecution::getNodeId, null)
                .set(JudgeTaskExecution::getTokenId, null)
                .set(JudgeTaskExecution::getAttemptId, null)
                .set(JudgeTaskExecution::getAttemptCount, 0)
                .set(JudgeTaskExecution::getMaxAttemptCount, maxAttemptCount())
                .set(JudgeTaskExecution::getStatus, JudgeTaskExecutionStatusConstant.QUEUED)
                .set(JudgeTaskExecution::getLeaseUntil, null)
                .set(JudgeTaskExecution::getExecutionDeadline, deadline)
                .set(JudgeTaskExecution::getLastError, null)
                .set(JudgeTaskExecution::getTerminalFingerprint, null));
    }

    private String resolveStreamKey(JudgeTaskOutbox outbox, JudgeTaskMessage message) {
        String streamKey = outbox.getStreamKey();
        if (streamKey != null && !streamKey.isBlank()) {
            return streamKey;
        }
        return streamService.resolveStreamKey(message.getJudgeMode());
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

    private JudgeTaskOutbox markProcessing(JudgeTaskOutbox outbox) {
        LocalDateTime now = LocalDateTime.now();
        // gmt_modified 同为 DATETIME(0)，用整秒下界避免小数进位造成“看似未超时”
        LocalDateTime staleProcessingTime = now.withNano(0).minusSeconds(processingTimeoutSeconds());
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
                    LocalDateTime.now().withNano(0).plusSeconds(retryBackoffSeconds()));
        }
        outboxMapper.update(null, wrapper);
    }

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

    private void validateTaskPayload(JudgeTaskMessage message) {
        String judgeMode = defaultString(message.getJudgeMode(), DEFAULT_JUDGE_MODE).toLowerCase();
        if (!FIXED_JUDGE_MODES.contains(judgeMode)) {
            // 只接受固定集合，禁止任意 mode 静默映射到 default Stream
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的判题模式: " + message.getJudgeMode());
        }
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

    private void validateBytes(String value, int maxBytes, String message) {
        if (value == null) {
            return;
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new BizException(ResultCode.BAD_REQUEST, message);
        }
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

    private int maxAttemptCount() {
        Integer value = streamProperties.getMaxAttemptCount();
        return value == null || value <= 0 ? DEFAULT_MAX_ATTEMPT_COUNT : value;
    }

    private long executionDeadlineSeconds() {
        Long value = streamProperties.getExecutionDeadlineSeconds();
        return value == null || value <= 0 ? 7200L : value;
    }

    private long retryBackoffSeconds() {
        Long value = submissionProperties.getJudgeOutbox().getRetryBackoffSeconds();
        return value == null || value <= 0 ? DEFAULT_RETRY_BACKOFF_SECONDS : value;
    }

    private long processingTimeoutSeconds() {
        Long value = submissionProperties.getJudgeOutbox().getProcessingTimeoutSeconds();
        return value == null || value <= 0 ? DEFAULT_PROCESSING_TIMEOUT_SECONDS : value;
    }

    private int maxCodeBytes() {
        Integer value = submissionProperties.getMaxCodeBytes();
        return value == null || value <= 0 ? DEFAULT_MAX_CODE_BYTES : value;
    }

    private int maxCheckerBytes() {
        Integer value = submissionProperties.getMaxCheckerBytes();
        return value == null || value <= 0 ? DEFAULT_MAX_CHECKER_BYTES : value;
    }

    private int maxInteractorBytes() {
        Integer value = submissionProperties.getMaxInteractorBytes();
        return value == null || value <= 0 ? DEFAULT_MAX_INTERACTOR_BYTES : value;
    }

    private int maxMessagePayloadBytes() {
        Integer value = submissionProperties.getMaxMessagePayloadBytes();
        return value == null || value <= 0 ? DEFAULT_MAX_MESSAGE_PAYLOAD_BYTES : value;
    }

    private String truncateError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
