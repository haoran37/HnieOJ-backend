package com.hnieacm.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/06
 * @Description: 判题消息队列配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "hnieoj.judge.mq")
public class JudgeMqProperties {

    private static final String DEFAULT_EXCHANGE = "hnieoj.judge.exchange";
    private static final String DEFAULT_TASK_QUEUE = "hnieoj.judge.task";
    private static final String DEFAULT_ROUTING_KEY = "judge.submission.created";
    private static final String DEFAULT_DEAD_LETTER_EXCHANGE = "hnieoj.judge.dlx";
    private static final String DEFAULT_DEAD_LETTER_QUEUE = "hnieoj.judge.task.dlq";
    private static final String DEFAULT_DEAD_LETTER_ROUTING_KEY = "judge.submission.created.dlq";

    private String exchange = DEFAULT_EXCHANGE;

    private String taskQueue = DEFAULT_TASK_QUEUE;

    private String routingKey = DEFAULT_ROUTING_KEY;

    private String deadLetterExchange = DEFAULT_DEAD_LETTER_EXCHANGE;

    private String deadLetterQueue = DEFAULT_DEAD_LETTER_QUEUE;

    private String deadLetterRoutingKey = DEFAULT_DEAD_LETTER_ROUTING_KEY;

    private Boolean consumerEnabled = Boolean.TRUE;
}
