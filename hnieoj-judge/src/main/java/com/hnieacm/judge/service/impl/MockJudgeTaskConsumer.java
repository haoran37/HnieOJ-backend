package com.hnieacm.judge.service.impl;

import com.hnieacm.common.dto.JudgeTaskMessage;
import com.hnieacm.judge.constant.JudgeStatusConstant;
import com.hnieacm.judge.entity.Judge;
import com.hnieacm.judge.mapper.JudgeMapper;
import com.hnieacm.judge.service.JudgeTaskConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/06
 * @Description: 判题任务 Mock 消费服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockJudgeTaskConsumer implements JudgeTaskConsumer {

    private final JudgeMapper judgeMapper;

    @Override
    public void consume(JudgeTaskMessage message) {
        if (message == null || message.getJudgeId() == null) {
            log.warn("Invalid judge task message ignored, message: {}", message);
            return;
        }
        Judge judge = judgeMapper.selectById(message.getJudgeId());
        if (judge == null) {
            log.warn("Judge task ignored because judge record not found, judgeId: {}, submissionId: {}",
                    message.getJudgeId(), message.getSubmissionId());
            return;
        }
        if (!Integer.valueOf(JudgeStatusConstant.PENDING).equals(judge.getStatus())) {
            log.info("Judge task ignored because status is not pending, judgeId: {}, submissionId: {}, status: {}",
                    judge.getId(), judge.getSubmitId(), judge.getStatus());
            return;
        }
        log.info("Judge task received, judgeId: {}, submissionId: {}, problemId: {}, problemCode: {}, language: {}",
                judge.getId(), judge.getSubmitId(), judge.getProblemId(), judge.getProblemCode(), judge.getLanguage());
    }
}
