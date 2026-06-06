package com.hnieacm.submission.config;

import com.hnieacm.common.properties.JudgeMqProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/06
 * @Description: 判题任务 RabbitMQ 配置
 */
@Configuration
@RequiredArgsConstructor
public class JudgeRabbitMqConfig {

    private final JudgeMqProperties properties;

    @Bean
    public DirectExchange judgeTaskExchange() {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public DirectExchange judgeDeadLetterExchange() {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    @Bean
    public Queue judgeTaskQueue() {
        return QueueBuilder.durable(properties.getTaskQueue())
                .deadLetterExchange(properties.getDeadLetterExchange())
                .deadLetterRoutingKey(properties.getDeadLetterRoutingKey())
                .build();
    }

    @Bean
    public Queue judgeTaskDeadLetterQueue() {
        return QueueBuilder.durable(properties.getDeadLetterQueue()).build();
    }

    @Bean
    public Binding judgeTaskBinding() {
        return BindingBuilder.bind(judgeTaskQueue()).to(judgeTaskExchange()).with(properties.getRoutingKey());
    }

    @Bean
    public Binding judgeDeadLetterBinding() {
        return BindingBuilder.bind(judgeTaskDeadLetterQueue())
                .to(judgeDeadLetterExchange())
                .with(properties.getDeadLetterRoutingKey());
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
