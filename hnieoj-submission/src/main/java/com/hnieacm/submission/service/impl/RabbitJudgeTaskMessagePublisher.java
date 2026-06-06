package com.hnieacm.submission.service.impl;

import com.hnieacm.common.dto.JudgeTaskMessage;
import com.hnieacm.common.properties.JudgeMqProperties;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.service.JudgeTaskMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    private final RabbitTemplate rabbitTemplate;
    private final JudgeMqProperties properties;

    @Override
    public void publishAfterCommit(Judge judge) {
        if (judge == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(judge);
                }
            });
            return;
        }
        publish(judge);
    }

    private void publish(Judge judge) {
        JudgeTaskMessage message = buildMessage(judge);
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

    private JudgeTaskMessage buildMessage(Judge judge) {
        JudgeTaskMessage message = new JudgeTaskMessage();
        message.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        message.setJudgeId(judge.getId());
        message.setSubmissionId(judge.getSubmitId());
        message.setProblemId(judge.getProblemId());
        message.setProblemCode(judge.getProblemCode());
        message.setUid(judge.getUid());
        message.setLanguage(judge.getLanguage());
        message.setCreatedAtMillis(System.currentTimeMillis());
        return message;
    }
}
