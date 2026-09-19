package com.hnieacm.submission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.service.SignedCaller;
import com.hnieacm.submission.constant.JudgeTaskExecutionStatusConstant;
import com.hnieacm.submission.constant.SubmissionStatusConstant;
import com.hnieacm.submission.dto.JudgeResultEventRequest;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeCase;
import com.hnieacm.submission.entity.JudgeTaskExecution;
import com.hnieacm.submission.entity.RejudgeTaskDetail;
import com.hnieacm.submission.mapper.JudgeCaseMapper;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.mapper.RejudgeTaskDetailMapper;
import com.hnieacm.submission.service.JudgeResultReportService;
import com.hnieacm.submission.service.JudgeTaskStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题结果回传服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeResultReportServiceImpl implements JudgeResultReportService {

    private static final String EVENT_STATUS_CHANGED = "STATUS_CHANGED";
    private static final String EVENT_CASE_FINISHED = "CASE_FINISHED";
    private static final String EVENT_JUDGE_FINISHED = "JUDGE_FINISHED";
    private static final String EVENT_JUDGE_FAILED = "JUDGE_FAILED";
    private static final String PROGRESS_TOPIC_PREFIX = "/topic/submissions/";
    private static final String PROGRESS_TOPIC_SUFFIX = "/progress";
    private static final Set<Integer> VALID_STATUS_SET = Set.of(
            SubmissionStatusConstant.PENDING,
            SubmissionStatusConstant.COMPILING,
            SubmissionStatusConstant.RUNNING,
            SubmissionStatusConstant.ACCEPTED,
            SubmissionStatusConstant.RUNTIME_ERROR,
            SubmissionStatusConstant.COMPILE_ERROR,
            SubmissionStatusConstant.WRONG_ANSWER,
            SubmissionStatusConstant.TIME_LIMIT_EXCEEDED,
            SubmissionStatusConstant.MEMORY_LIMIT_EXCEEDED,
            SubmissionStatusConstant.SYSTEM_ERROR,
            SubmissionStatusConstant.JUDGEMENT_FAILED,
            SubmissionStatusConstant.INVALID_INTERACTION
    );

    private final JudgeMapper judgeMapper;
    private final JudgeCaseMapper judgeCaseMapper;
    private final RejudgeTaskDetailMapper rejudgeTaskDetailMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final JudgeTaskLeaseManager leaseManager;
    private final JudgeTaskStreamService streamService;

    /**
     * @MethodName handleEvent
     * @Param submissionId
     * @Param request
     * @Param caller
     * @Description 处理事件：先在事务内校验身份/轮次/尝试/租约所有权，终态落库后才 ACK Redis
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleEvent(String submissionId, JudgeResultEventRequest request, SignedCaller caller) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "判题事件不能为空");
        }
        String normalizedSubmissionId = StrUtil.trimToNull(submissionId);
        String bodySubmissionId = StrUtil.trimToNull(request.getSubmissionId());
        if (normalizedSubmissionId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "submissionId 不能为空");
        }
        if (bodySubmissionId != null && !normalizedSubmissionId.equals(bodySubmissionId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "submissionId 不一致");
        }
        request.setSubmissionId(normalizedSubmissionId);

        String eventType = StrUtil.trimToNull(request.getEventType());
        if (eventType == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "eventType 不能为空");
        }
        request.setEventType(eventType);
        // JUDGE_FAILED 允许省略 status，落库语义为 SYSTEM_ERROR；归一化后终态重报才能一致比较
        if (EVENT_JUDGE_FAILED.equals(eventType) && request.getStatus() == null) {
            request.setStatus(SubmissionStatusConstant.SYSTEM_ERROR);
        }
        validateEvent(request);

        String judgeTaskId = StrUtil.trimToNull(request.getJudgeTaskId());
        String attemptId = StrUtil.trimToNull(request.getAttemptId());
        if (judgeTaskId == null || attemptId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "judgeTaskId/attemptId 不能为空");
        }
        request.setJudgeTaskId(judgeTaskId);
        request.setAttemptId(attemptId);
        boolean terminalEvent = EVENT_JUDGE_FINISHED.equals(eventType) || EVENT_JUDGE_FAILED.equals(eventType);

        JudgeTaskExecution execution = leaseManager.validateEventOwnership(
                normalizedSubmissionId, caller, judgeTaskId, attemptId, terminalEvent);

        Judge judge = queryJudge(normalizedSubmissionId);
        if (!judgeTaskId.equals(StrUtil.trimToNull(judge.getJudgeTaskId()))) {
            throw new BizException(ResultCode.FORBIDDEN, "判题任务轮次不匹配");
        }

        // 终态同身份同 attempt 同最终业务内容重报幂等成功，不重写；仅比较落库的终态业务指纹，忽略纯传输时间戳
        if (JudgeTaskExecutionStatusConstant.COMPLETED.equals(execution.getStatus())
                || isTerminalStatus(judge.getStatus())) {
            String incomingFingerprint = terminalEvent ? terminalFingerprint(request, eventType) : null;
            if (!terminalEvent || incomingFingerprint == null
                    || !incomingFingerprint.equals(execution.getTerminalFingerprint())) {
                throw new BizException(ResultCode.FORBIDDEN, "判题结果已提交且不一致");
            }
            // 首次终态已写入指纹、释放租约并完成状态；同内容重报只 ACK，禁止再次写已完成状态
            ackAfterCommit(execution);
            return;
        }

        switch (eventType) {
            case EVENT_STATUS_CHANGED -> updateJudgeProgress(judge, request);
            case EVENT_CASE_FINISHED -> handleCaseFinished(judge, request);
            case EVENT_JUDGE_FINISHED -> handleJudgeFinished(judge, request);
            case EVENT_JUDGE_FAILED -> handleJudgeFailed(judge, request);
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的判题事件类型");
        }

        if (terminalEvent) {
            leaseManager.complete(execution, terminalFingerprint(request, eventType));
            ackAfterCommit(execution);
        } else {
            leaseManager.markRunning(execution);
        }
        pushAfterCommit(normalizedSubmissionId, request);
    }

    /**
     * @MethodName ackAfterCommit
     * @Param execution
     * @Description 仅在 MySQL 事务提交后 XACK；Redis 失败不影响已落库结果
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    private void ackAfterCommit(JudgeTaskExecution execution) {
        if (execution == null) {
            return;
        }
        String streamKey = execution.getStreamKey();
        String recordId = execution.getStreamId();
        if (streamKey == null || recordId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    streamService.ack(streamKey, recordId);
                }
            });
            return;
        }
        streamService.ack(streamKey, recordId);
    }

    /**
     * @MethodName pushAfterCommit
     * @Param submissionId
     * @Param request
     * @Description 事务提交后再推送进度，避免前端看到未提交事件
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    private void pushAfterCommit(String submissionId, JudgeResultEventRequest request) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend(PROGRESS_TOPIC_PREFIX + submissionId + PROGRESS_TOPIC_SUFFIX, request);
                }
            });
            return;
        }
        messagingTemplate.convertAndSend(PROGRESS_TOPIC_PREFIX + submissionId + PROGRESS_TOPIC_SUFFIX, request);
    }

    /**
     * @MethodName queryJudge
     * @Param submissionId
     * @Description 查询判断
     * @Return @return {@link Judge }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private Judge queryJudge(String submissionId) {
        Judge judge = judgeMapper.selectOne(new LambdaQueryWrapper<Judge>()
                .eq(Judge::getSubmitId, submissionId)
                .last("limit 1"));
        if (judge == null) {
            throw new BizException(ResultCode.SUBMISSION_NOT_FOUND, "提交记录不存在");
        }
        return judge;
    }

    /**
     * @MethodName handleCaseFinished
     * @Param judge
     * @Param request
     * @Description 处理单个测试点完成事件
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void handleCaseFinished(Judge judge, JudgeResultEventRequest request) {
        JudgeResultEventRequest.CaseResult caseResult = request.getCaseResult();
        if (caseResult == null || StrUtil.isBlank(caseResult.getCaseId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "caseResult 不能为空");
        }
        upsertJudgeCase(judge, caseResult);
        updateJudgeProgress(judge, request);
    }

    /**
     * @MethodName handleJudgeFinished
     * @Param judge
     * @Param request
     * @Description 裁判结束
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void handleJudgeFinished(Judge judge, JudgeResultEventRequest request) {
        updateJudge(judge, request, request.getScore(), null, StrUtil.trimToNull(request.getDiagnosticMessage()));
    }

    /**
     * @MethodName handleJudgeFailed
     * @Param judge
     * @Param request
     * @Description 处理判断失败
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void handleJudgeFailed(Judge judge, JudgeResultEventRequest request) {
        if (request.getStatus() == null) {
            request.setStatus(SubmissionStatusConstant.SYSTEM_ERROR);
        }
        updateJudge(judge, request, request.getScore(), StrUtil.trimToNull(request.getMessage()),
                StrUtil.trimToNull(request.getDiagnosticMessage()));
    }

    /**
     * @MethodName updateJudgeProgress
     * @Param judge
     * @Param request
     * @Description 更新裁判进度
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void updateJudgeProgress(Judge judge, JudgeResultEventRequest request) {
        updateJudge(judge, request, null, null, null);
    }

    /**
     * @MethodName updateJudge
     * @Param judge
     * @Param request
     * @Param score
     * @Param errorMessage
     * @Param diagnosticMessage
     * @Description 更新judge
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void updateJudge(Judge judge, JudgeResultEventRequest request, Integer score, String errorMessage,
                             String diagnosticMessage) {
        LambdaUpdateWrapper<Judge> wrapper = new LambdaUpdateWrapper<Judge>()
                .eq(Judge::getId, judge.getId())
                .lt(Judge::getStatus, SubmissionStatusConstant.ACCEPTED);
        String judgeTaskId = StrUtil.trimToNull(judge.getJudgeTaskId());
        if (judgeTaskId != null) {
            wrapper.eq(Judge::getJudgeTaskId, judgeTaskId);
        }
        appendStatusUpdate(wrapper, request.getStatus());
        appendProgressUpdate(wrapper, "total_case", request.getTotalCase());
        appendProgressUpdate(wrapper, "judged_case", request.getJudgedCase());
        appendProgressUpdate(wrapper, "current_case", request.getCurrentCase());
        appendCaseMetricUpdate(wrapper, request.getCaseResult());
        if (score != null) {
            wrapper.set(Judge::getScore, score);
        }
        if (errorMessage != null) {
            wrapper.set(Judge::getErrorMessage, errorMessage);
        }
        if (diagnosticMessage != null) {
            wrapper.set(Judge::getDiagnosticMessage, diagnosticMessage);
        }
        int updated = judgeMapper.update(null, wrapper);
        if (updated == 0) {
            log.info("Judge event update skipped, submissionId: {}, eventType: {}, judgeTaskId: {}",
                    judge.getSubmitId(), request.getEventType(), judge.getJudgeTaskId());
            return;
        }
        if (isTerminalStatus(request.getStatus())) {
            freezeRejudgeTaskDetail(judge.getId(), judge.getJudgeTaskId());
        }
    }

    /**
     * @MethodName freezeRejudgeTaskDetail
     * @Param judgeId
     * @Param judgeTaskId
     * @Description 冻结重新评估任务详细信息
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void freezeRejudgeTaskDetail(Long judgeId, String judgeTaskId) {
        String normalizedJudgeTaskId = StrUtil.trimToNull(judgeTaskId);
        if (judgeId == null || normalizedJudgeTaskId == null) {
            return;
        }
        Judge latest = judgeMapper.selectById(judgeId);
        if (latest == null) {
            return;
        }
        rejudgeTaskDetailMapper.update(null, new LambdaUpdateWrapper<RejudgeTaskDetail>()
                .eq(RejudgeTaskDetail::getJudgeId, judgeId)
                .eq(RejudgeTaskDetail::getJudgeTaskId, normalizedJudgeTaskId)
                .set(RejudgeTaskDetail::getFinalStatus, latest.getStatus())
                .set(RejudgeTaskDetail::getFinalScore, latest.getScore())
                .set(RejudgeTaskDetail::getFinalTime, latest.getTime())
                .set(RejudgeTaskDetail::getFinalMemory, latest.getMemory())
                .set(RejudgeTaskDetail::getFinishedTime, LocalDateTime.now()));
    }

    /**
     * @MethodName upsertJudgeCase
     * @Param judge
     * @Param result
     * @Description 持久化单个测试点结果
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void upsertJudgeCase(Judge judge, JudgeResultEventRequest.CaseResult result) {
        String caseId = StrUtil.trim(result.getCaseId());
        JudgeCase exist = judgeCaseMapper.selectOne(new LambdaQueryWrapper<JudgeCase>()
                .eq(JudgeCase::getSubmitId, judge.getId())
                .eq(JudgeCase::getCaseId, caseId)
                .last("limit 1"));
        JudgeCase entity = exist == null ? new JudgeCase() : exist;
        entity.setSubmitId(judge.getId());
        entity.setCaseId(caseId);
        entity.setStatus(result.getStatus());
        entity.setTime(toInteger(result.getTime()));
        entity.setMemory(toInteger(result.getMemory()));
        entity.setScore(defaultZero(result.getScore()));
        entity.setUserOutput(StrUtil.trimToNull(result.getUserOutput()));
        if (exist == null) {
            try {
                judgeCaseMapper.insert(entity);
            } catch (DuplicateKeyException e) {
                updateJudgeCase(judge.getId(), caseId, entity);
                refreshJudgeCaseMetrics(judge);
            }
        } else {
            judgeCaseMapper.updateById(entity);
            refreshJudgeCaseMetrics(judge);
        }
    }

    /**
     * @MethodName updateJudgeCase
     * @Param judgeId
     * @Param caseId
     * @Param entity
     * @Description 根据指定提交ID和用例ID更新判题案例记录
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void updateJudgeCase(Long judgeId, String caseId, JudgeCase entity) {
        judgeCaseMapper.update(entity, new LambdaUpdateWrapper<JudgeCase>()
                .eq(JudgeCase::getSubmitId, judgeId)
                .eq(JudgeCase::getCaseId, caseId));
    }

    /**
     * @MethodName refreshJudgeCaseMetrics
     * @Param judge
     * @Description 聚合所有测试点的最大耗时与内存
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void refreshJudgeCaseMetrics(Judge judge) {
        List<JudgeCase> cases = judgeCaseMapper.selectList(new LambdaQueryWrapper<JudgeCase>()
                .eq(JudgeCase::getSubmitId, judge.getId()));
        Integer maxTime = cases.stream()
                .map(JudgeCase::getTime)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
        Integer maxMemory = cases.stream()
                .map(JudgeCase::getMemory)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
        LambdaUpdateWrapper<Judge> wrapper = new LambdaUpdateWrapper<Judge>()
                .eq(Judge::getId, judge.getId())
                .lt(Judge::getStatus, SubmissionStatusConstant.ACCEPTED)
                .set(Judge::getTime, maxTime)
                .set(Judge::getMemory, maxMemory);
        String judgeTaskId = StrUtil.trimToNull(judge.getJudgeTaskId());
        if (judgeTaskId != null) {
            wrapper.eq(Judge::getJudgeTaskId, judgeTaskId);
        }
        judgeMapper.update(null, wrapper);
    }

    /**
     * @MethodName terminalFingerprint
     * @Param request
     * @Param eventType
     * @Description 计算终态业务内容指纹：包含事件类型、状态、分数、进度、消息、诊断与测试点等业务字段；
     * 仅忽略纯传输时间戳 eventTime，空值按已定义默认值归一化（不能用“省略字段”通配任意值）。
     * 指纹在首次终态落库的同一事务写入执行记录，重报时按同一规范重新计算比较。
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    private String terminalFingerprint(JudgeResultEventRequest request, String eventType) {
        StringBuilder canonical = new StringBuilder(256);
        appendCanonical(canonical, "eventType", eventType);
        appendCanonical(canonical, "status", request.getStatus());
        appendCanonical(canonical, "statusText", StrUtil.trimToNull(request.getStatusText()));
        appendCanonical(canonical, "totalCase", defaultZero(request.getTotalCase()));
        appendCanonical(canonical, "judgedCase", defaultZero(request.getJudgedCase()));
        appendCanonical(canonical, "currentCase", defaultZero(request.getCurrentCase()));
        appendCanonical(canonical, "score", defaultZero(request.getScore()));
        appendCanonical(canonical, "message", StrUtil.trimToNull(request.getMessage()));
        appendCanonical(canonical, "diagnosticMessage", StrUtil.trimToNull(request.getDiagnosticMessage()));
        JudgeResultEventRequest.CaseResult caseResult = request.getCaseResult();
        if (caseResult == null) {
            appendCanonical(canonical, "caseResult", null);
        } else {
            appendCanonical(canonical, "caseId", StrUtil.trimToNull(caseResult.getCaseId()));
            appendCanonical(canonical, "caseStatus", caseResult.getStatus());
            appendCanonical(canonical, "caseStatusText", StrUtil.trimToNull(caseResult.getStatusText()));
            appendCanonical(canonical, "caseTime", caseResult.getTime());
            appendCanonical(canonical, "caseMemory", caseResult.getMemory());
            appendCanonical(canonical, "caseScore", defaultZero(caseResult.getScore()));
            appendCanonical(canonical, "caseUserOutput", StrUtil.trimToNull(caseResult.getUserOutput()));
        }
        return sha256Hex(canonical.toString());
    }

    /**
     * @MethodName appendCanonical
     * @Param canonical
     * @Param name
     * @Param value
     * @Description 逐行追加“字段名=长度:值”，长度前缀避免分隔符歧义；null 使用不可能的长度 -1 编码，
     * 与非空值的任意字面量（包括 "<null>"）结构上区分，避免缺省与字面量哨兵碰撞，保证同一业务内容得到稳定规范串
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    private void appendCanonical(StringBuilder canonical, String name, Object value) {
        if (value == null) {
            canonical.append(name).append("=-1:\n");
            return;
        }
        String text = String.valueOf(value);
        canonical.append(name).append('=').append(text.length()).append(':').append(text).append('\n');
    }

    /**
     * @MethodName sha256Hex
     * @Param value
     * @Description JDK 内置 SHA-256 十六进制摘要，供终态幂等指纹比较
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * @MethodName validateEvent
     * @Param request
     * @Description 验证事件
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void validateEvent(JudgeResultEventRequest request) {
        validateStatus(request.getStatus(), "status");
        validateProgress(request.getTotalCase(), "totalCase");
        validateProgress(request.getJudgedCase(), "judgedCase");
        validateProgress(request.getCurrentCase(), "currentCase");
        validateProgress(request.getScore(), "score");
        if (request.getTotalCase() != null && request.getTotalCase() > 0) {
            if (request.getJudgedCase() != null && request.getJudgedCase() > request.getTotalCase()) {
                throw new BizException(ResultCode.BAD_REQUEST, "judgedCase 不能大于 totalCase");
            }
            if (request.getCurrentCase() != null && request.getCurrentCase() > request.getTotalCase()) {
                throw new BizException(ResultCode.BAD_REQUEST, "currentCase 不能大于 totalCase");
            }
        }
        switch (request.getEventType()) {
            case EVENT_STATUS_CHANGED -> validateNonTerminalStatus(request.getStatus(), "status");
            case EVENT_CASE_FINISHED -> {
                if (request.getStatus() != null
                        && !Integer.valueOf(SubmissionStatusConstant.RUNNING).equals(request.getStatus())) {
                    throw new BizException(ResultCode.BAD_REQUEST, "测试点事件主状态必须为 Running");
                }
                validateCaseResult(request.getCaseResult());
            }
            case EVENT_JUDGE_FINISHED -> {
                validateRequiredTerminalStatus(request.getStatus(), "status");
                if (request.getScore() == null) {
                    throw new BizException(ResultCode.BAD_REQUEST, "score 不能为空");
                }
            }
            case EVENT_JUDGE_FAILED -> validateTerminalStatus(request.getStatus(), "status");
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的判题事件类型");
        }
    }

    /**
     * @MethodName validateCaseResult
     * @Param caseResult
     * @Description 验证案例结果
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void validateCaseResult(JudgeResultEventRequest.CaseResult caseResult) {
        if (caseResult == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "caseResult 不能为空");
        }
        validateStatus(caseResult.getStatus(), "caseResult.status");
        validateRequiredTerminalStatus(caseResult.getStatus(), "caseResult.status");
        validateNonNegative(caseResult.getTime(), "caseResult.time");
        validateNonNegative(caseResult.getMemory(), "caseResult.memory");
        validateProgress(caseResult.getScore(), "caseResult.score");
    }

    /**
     * @MethodName validateStatus
     * @Param status
     * @Param fieldName
     * @Description 验证状态
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void validateStatus(Integer status, String fieldName) {
        if (status != null && !VALID_STATUS_SET.contains(status)) {
            throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 不合法");
        }
    }

    /**
     * @MethodName validateRequiredTerminalStatus
     * @Param status
     * @Param fieldName
     * @Description 验证所需终端状态
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void validateRequiredTerminalStatus(Integer status, String fieldName) {
        if (status == null) {
            throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 不能为空");
        }
        validateTerminalStatus(status, fieldName);
    }

    /**
     * @MethodName validateTerminalStatus
     * @Param status
     * @Param fieldName
     * @Description 验证终端状态
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void validateTerminalStatus(Integer status, String fieldName) {
        if (status != null && !isTerminalStatus(status)) {
            throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 必须为终态");
        }
    }

    /**
     * @MethodName validateNonTerminalStatus
     * @Param status
     * @Param fieldName
     * @Description 验证非终端状态
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void validateNonTerminalStatus(Integer status, String fieldName) {
        if (isTerminalStatus(status)) {
            throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 不能为终态");
        }
    }

    /**
     * @MethodName validateProgress
     * @Param value
     * @Param fieldName
     * @Description 验证进度
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void validateProgress(Integer value, String fieldName) {
        if (value != null && value < 0) {
            throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 不能小于 0");
        }
    }

    /**
     * @MethodName validateNonNegative
     * @Param value
     * @Param fieldName
     * @Description 校验数值非负
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void validateNonNegative(Long value, String fieldName) {
        if (value != null && value < 0) {
            throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 不能小于 0");
        }
    }

    /**
     * @MethodName isTerminalStatus
     * @Param status
     * @Description 判断状态是否为终态
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private boolean isTerminalStatus(Integer status) {
        return status != null && status >= SubmissionStatusConstant.ACCEPTED;
    }

    /**
     * @MethodName defaultZero
     * @Param value
     * @Description 默认值为零
     * @Return @return {@link Integer }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private Integer defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * @MethodName appendStatusUpdate
     * @Param wrapper
     * @Param status
     * @Description 附加状态更新
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void appendStatusUpdate(LambdaUpdateWrapper<Judge> wrapper, Integer status) {
        if (status == null) {
            return;
        }
        if (isTerminalStatus(status)) {
            wrapper.set(Judge::getStatus, status);
            return;
        }
        wrapper.setSql("status = GREATEST(COALESCE(status, "
                + SubmissionStatusConstant.PENDING + "), " + status + ")");
    }

    /**
     * @MethodName appendProgressUpdate
     * @Param wrapper
     * @Param columnName
     * @Param value
     * @Description 附加进度更新
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void appendProgressUpdate(LambdaUpdateWrapper<Judge> wrapper, String columnName, Integer value) {
        if (value == null) {
            return;
        }
        wrapper.setSql(columnName + " = GREATEST(COALESCE(" + columnName + ", 0), " + value + ")");
    }

    /**
     * @MethodName appendCaseMetricUpdate
     * @Param wrapper
     * @Param result
     * @Description 附加案例指标更新
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void appendCaseMetricUpdate(LambdaUpdateWrapper<Judge> wrapper, JudgeResultEventRequest.CaseResult result) {
        if (result == null) {
            return;
        }
        Integer time = toInteger(result.getTime());
        if (time != null) {
            wrapper.setSql("time = GREATEST(COALESCE(time, 0), " + time + ")");
        }
        Integer memory = toInteger(result.getMemory());
        if (memory != null) {
            wrapper.setSql("memory = GREATEST(COALESCE(memory, 0), " + memory + ")");
        }
    }

    /**
     * @MethodName toInteger
     * @Param value
     * @Description 转换为整数
     * @Return @return {@link Integer }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private Integer toInteger(Long value) {
        if (value == null) {
            return null;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return value.intValue();
    }
}
