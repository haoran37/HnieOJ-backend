package com.hnieacm.submission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.submission.constant.SubmissionStatusConstant;
import com.hnieacm.submission.dto.JudgeResultEventRequest;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeCase;
import com.hnieacm.submission.mapper.JudgeCaseMapper;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.service.JudgeResultReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    private final JudgeMapper judgeMapper;
    private final JudgeCaseMapper judgeCaseMapper;
    private final SimpMessagingTemplate messagingTemplate;

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

        switch (eventType) {
            case EVENT_STATUS_CHANGED -> updateJudgeProgress(judge, request);
            case EVENT_CASE_FINISHED -> handleCaseFinished(judge, request);
            case EVENT_JUDGE_FINISHED -> handleJudgeFinished(judge, request);
            case EVENT_JUDGE_FAILED -> handleJudgeFailed(judge, request);
            default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的判题事件类型");
        }
        messagingTemplate.convertAndSend(PROGRESS_TOPIC_PREFIX + normalizedSubmissionId + PROGRESS_TOPIC_SUFFIX, request);
    }

    private Judge queryJudge(String submissionId) {
        Judge judge = judgeMapper.selectOne(new LambdaQueryWrapper<Judge>()
                .eq(Judge::getSubmitId, submissionId)
                .last("limit 1"));
        if (judge == null) {
            throw new BizException(ResultCode.SUBMISSION_NOT_FOUND, "提交记录不存在");
        }
        return judge;
    }

    private void handleCaseFinished(Judge judge, JudgeResultEventRequest request) {
        JudgeResultEventRequest.CaseResult caseResult = request.getCaseResult();
        if (caseResult == null || StrUtil.isBlank(caseResult.getCaseId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "caseResult 不能为空");
        }
        upsertJudgeCase(judge, caseResult);
        updateJudgeProgress(judge, request);
    }

    private void handleJudgeFinished(Judge judge, JudgeResultEventRequest request) {
        Integer score = request.getScore() == null ? sumScore(judge.getId()) : request.getScore();
        updateJudge(judge, request, score, null);
    }

    private void handleJudgeFailed(Judge judge, JudgeResultEventRequest request) {
        updateJudge(judge, request, request.getScore(), StrUtil.trimToNull(request.getMessage()));
    }

    private void updateJudgeProgress(Judge judge, JudgeResultEventRequest request) {
        updateJudge(judge, request, null, null);
    }

    private void updateJudge(Judge judge, JudgeResultEventRequest request, Integer score, String errorMessage) {
        Judge update = new Judge();
        update.setId(judge.getId());
        update.setStatus(request.getStatus() == null ? judge.getStatus() : request.getStatus());
        update.setTotalCase(defaultZero(request.getTotalCase()));
        update.setJudgedCase(defaultZero(request.getJudgedCase()));
        update.setCurrentCase(defaultZero(request.getCurrentCase()));
        update.setTime(maxCaseTime(judge.getId()));
        update.setMemory(maxCaseMemory(judge.getId()));
        if (score != null) {
            update.setScore(score);
        }
        if (errorMessage != null) {
            update.setErrorMessage(errorMessage);
        }
        if (EVENT_JUDGE_FAILED.equals(request.getEventType()) && update.getStatus() == null) {
            update.setStatus(SubmissionStatusConstant.SYSTEM_ERROR);
        }
        judgeMapper.updateById(update);
    }

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
            judgeCaseMapper.insert(entity);
        } else {
            judgeCaseMapper.updateById(entity);
        }
    }

    private Integer maxCaseTime(Long judgeId) {
        return listCases(judgeId).stream()
                .map(JudgeCase::getTime)
                .filter(item -> item != null)
                .max(Integer::compareTo)
                .orElse(null);
    }

    private Integer maxCaseMemory(Long judgeId) {
        return listCases(judgeId).stream()
                .map(JudgeCase::getMemory)
                .filter(item -> item != null)
                .max(Integer::compareTo)
                .orElse(null);
    }

    private Integer sumScore(Long judgeId) {
        return listCases(judgeId).stream()
                .map(JudgeCase::getScore)
                .filter(item -> item != null)
                .reduce(0, Integer::sum);
    }

    private List<JudgeCase> listCases(Long judgeId) {
        return judgeCaseMapper.selectList(new LambdaQueryWrapper<JudgeCase>()
                .eq(JudgeCase::getSubmitId, judgeId));
    }

    private Integer defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

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
