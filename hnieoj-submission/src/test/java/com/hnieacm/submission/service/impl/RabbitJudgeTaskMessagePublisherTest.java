package com.hnieacm.submission.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.dto.JudgeTaskMessage;
import com.hnieacm.common.properties.JudgeMqProperties;
import com.hnieacm.submission.dto.ProblemBasicDto;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeTaskOutbox;
import com.hnieacm.submission.mapper.JudgeTaskOutboxMapper;
import com.hnieacm.submission.properties.SubmissionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: RabbitMQ 判题任务消息发布测试
 */
@ExtendWith(MockitoExtension.class)
class RabbitJudgeTaskMessagePublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private JudgeTaskOutboxMapper outboxMapper;

    @Test
    void shouldWriteSpjCheckerIntoOutboxPayload() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RabbitJudgeTaskMessagePublisher publisher = new RabbitJudgeTaskMessagePublisher(
                rabbitTemplate, new JudgeMqProperties(), outboxMapper, objectMapper, new SubmissionProperties());

        publisher.publishAfterCommit(buildJudge(), buildSpjProblem());

        ArgumentCaptor<JudgeTaskOutbox> outboxCaptor = ArgumentCaptor.forClass(JudgeTaskOutbox.class);
        verify(outboxMapper).insert(outboxCaptor.capture());
        JudgeTaskMessage message = objectMapper.readValue(outboxCaptor.getValue().getPayload(), JudgeTaskMessage.class);
        assertThat(message.getJudgeMode()).isEqualTo("spj");
        assertThat(message.getChecker()).isNotNull();
        assertThat(message.getChecker().getLanguage()).isEqualTo("cpp17");
        assertThat(message.getChecker().getSource()).contains("checker");
        assertThat(message.getChecker().getTimeLimit()).isEqualTo(3000);
        assertThat(message.getChecker().getMemoryLimit()).isEqualTo(512);
        assertThat(message.getChecker().getStackLimit()).isEqualTo(256);
        assertThat(message.getChecker().getOutputLimit()).isEqualTo(1048576);
        assertThat(message.getChecker().getProtocol()).isEqualTo("testlib");
    }

    private Judge buildJudge() {
        Judge judge = new Judge();
        judge.setId(1L);
        judge.setSubmitId("submission-1");
        judge.setJudgeTaskId("task-1");
        judge.setProblemId(10L);
        judge.setProblemCode("P1006");
        judge.setUid("20230001");
        judge.setLanguage("cpp");
        judge.setCode("int main() { return 0; }");
        return judge;
    }

    private ProblemBasicDto buildSpjProblem() {
        ProblemBasicDto problem = new ProblemBasicDto();
        problem.setJudgeMode("spj");
        problem.setSpjLanguage("cpp17");
        problem.setSpjCode("// checker");
        problem.setSpjTimeLimit(3000);
        problem.setSpjMemoryLimit(512);
        problem.setSpjStackLimit(256);
        problem.setSpjOutputLimit(1048576);
        problem.setSpjProtocol("testlib");
        return problem;
    }
}
