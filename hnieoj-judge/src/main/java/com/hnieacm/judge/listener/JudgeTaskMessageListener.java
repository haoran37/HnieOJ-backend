package com.hnieacm.judge.listener;

import com.hnieacm.common.dto.JudgeTaskMessage;
import com.hnieacm.judge.service.JudgeTaskConsumer;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/06
 * @Description: 判题任务消息监听器
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hnieoj.judge.mq", name = "consumer-enabled", havingValue = "true", matchIfMissing = true)
public class JudgeTaskMessageListener {

    private final JudgeTaskConsumer judgeTaskConsumer;

    @RabbitListener(queues = "${hnieoj.judge.mq.task-queue:hnieoj.judge.task}", ackMode = "MANUAL")
    public void onMessage(JudgeTaskMessage taskMessage, Message amqpMessage, Channel channel) throws IOException {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        try {
            if (taskMessage == null || taskMessage.getJudgeId() == null) {
                log.warn("Invalid judge task message ignored, deliveryTag: {}", deliveryTag);
                channel.basicAck(deliveryTag, false);
                return;
            }
            judgeTaskConsumer.consume(taskMessage);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Consume judge task message failed, deliveryTag: {}, taskMessage: {}",
                    deliveryTag, taskMessage, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
