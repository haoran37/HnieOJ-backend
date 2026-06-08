package com.hnieacm.submission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.submission.constant.SubmissionStatusConstant;
import com.hnieacm.submission.dto.JudgeResultEventRequest;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeCase;
import com.hnieacm.submission.entity.RejudgeTaskDetail;
import com.hnieacm.submission.mapper.JudgeCaseMapper;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.mapper.RejudgeTaskDetailMapper;
import com.hnieacm.submission.service.JudgeResultReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    /**
     * @MethodName handleEvent
     * @Param submissionId
     * @Param request
     * @Description 处理事件
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleEvent(String submissionId, JudgeResultEventRequest request) {
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

        Judge judge = queryJudge(normalizedSubmissionId);
        String eventType = StrUtil.trimToNull(request.getEventType());
        if (eventType == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "eventType 不能为空");
        }
        request.setEventType(eventType);
        validateEvent(request);

        if (!isCurrentJudgeTask(judge, request)) {
            log.info("Judge event ignored because task id mismatch, submissionId: {}, eventType: {}, currentTaskId: {}, incomingTaskId: {}",
                    normalizedSubmissionId, eventType, judge.getJudgeTaskId(), request.getJudgeTaskId());
            return;
        }

        // 已进入终态的提交不再接收进度类回写，避免乱序重试事件污染最终结果。
        if (isTerminalStatus(judge.getStatus())) {
            log.info("Judge event ignored because submission is terminal, submissionId: {}, eventType: {}, currentStatus: {}",
                    normalizedSubmissionId, eventType, judge.getStatus());
            return;
        }

        switch (eventType) {
            case EVENT_STATUS_CHANGED -> updateJudgeProgress(judge, request);
            case EVENT_CASE_FINISHED -> handleCaseFinished(judge, request);
            case EVENT_JUDGE_FINISHED -> handleJudgeFinished(judge, request);
            case EVENT_JUDGE_FAILED -> handleJudgeFailed(judge, request);
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的判题事件类型");
        }
        messagingTemplate.convertAndSend(PROGRESS_TOPIC_PREFIX + normalizedSubmissionId + PROGRESS_TOPIC_SUFFIX, request);
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
     * @MethodName isCurrentJudgeTask
     * @Param judge
     * @Param request
     * @Description 校验请求中的判题任务ID是否与当前记录一致
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private boolean isCurrentJudgeTask(Judge judge, JudgeResultEventRequest request) {
        String currentTaskId = StrUtil.trimToNull(judge.getJudgeTaskId());
        if (currentTaskId == null) {
            return true;
        }
        String incomingTaskId = StrUtil.trimToNull(request.getJudgeTaskId());
        request.setJudgeTaskId(incomingTaskId);
        return currentTaskId.equals(incomingTaskId);
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
