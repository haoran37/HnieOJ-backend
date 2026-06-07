package com.hnieacm.submission.service.impl;

import com.hnieacm.common.dto.JudgeTaskMessage;
import com.hnieacm.common.properties.JudgeMqProperties;
import com.hnieacm.submission.dto.ProblemBasicDto;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.service.JudgeTaskMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    private final RabbitTemplate rabbitTemplate;
    private final JudgeMqProperties properties;

    @Override
    public void publishAfterCommit(Judge judge, ProblemBasicDto problem) {
        if (judge == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(judge, problem);
                }
            });
            return;
        }
        publish(judge, problem);
    }

    private void publish(Judge judge, ProblemBasicDto problem) {
        JudgeTaskMessage message = buildMessage(judge, problem);
        try {
            rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), message, item -> {
                item.getMessageProperties().setMessageId(message.getMessageId());
                item.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return item;
            });
            log.info("Judge task message published, submissionId: {}, judgeId: {}, messageId: {}",
                    message.getSubmissionId(), message.getJudgeId(), message.getMessageId());
        } catch (Exception e) {
            log.error("Publish judge task message failed, submissionId: {}, judgeId: {}",
                    message.getSubmissionId(), message.getJudgeId(), e);
        }
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
        message.setIoScore(defaultInteger(problem == null ? null : problem.getIoScore(), DEFAULT_IO_SCORE));
        message.setIsRemoveEndBlank(problem == null || !Boolean.FALSE.equals(problem.getIsRemoveEndBlank()));
        message.setDataVersion(defaultInteger(problem == null ? null : problem.getDataVersion(), DEFAULT_DATA_VERSION));
        message.setCreatedAt(OffsetDateTime.now(ZoneId.systemDefault()).toString());
        message.setCreatedAtMillis(System.currentTimeMillis());
        return message;
    }

    private Integer defaultInteger(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
