package com.hnieacm.submission.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.dto.JudgeTaskMessage;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.properties.JudgeStreamProperties;
import com.hnieacm.submission.dto.ProblemBasicDto;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeTaskOutbox;
import com.hnieacm.submission.mapper.JudgeTaskExecutionMapper;
import com.hnieacm.submission.mapper.JudgeTaskOutboxMapper;
import com.hnieacm.submission.properties.SubmissionProperties;
import com.hnieacm.submission.service.JudgeTaskStreamService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: Redis Streams 判题任务发布测试：迁移原 Rabbit 发布器的 payload/SPJ/交互/大小/模式校验
 */
@ExtendWith(MockitoExtension.class)
class RedisJudgeTaskMessagePublisherTest {

    @Mock
    private JudgeTaskStreamService streamService;

    @Mock
    private JudgeTaskOutboxMapper outboxMapper;

    @Mock
    private JudgeTaskExecutionMapper executionMapper;

    @BeforeEach
    void setUp() {
        initTableInfo(JudgeTaskOutbox.class);
    }

    @Test
    void shouldWriteSpjCheckerIntoOutboxPayload() throws Exception {
        when(streamService.resolveStreamKey("spj")).thenReturn("hnieoj:judge:task:spj");
        ObjectMapper objectMapper = new ObjectMapper();
        RedisJudgeTaskMessagePublisher publisher = newPublisher(objectMapper, new SubmissionProperties());

        publisher.publishAfterCommit(buildJudge(), buildSpjProblem());

        JudgeTaskMessage message = capturedMessage(objectMapper, publisher);
        assertThat(message.getSchemaVersion()).isEqualTo(2);
        assertThat(message.getJudgeMode()).isEqualTo("spj");
        assertThat(message.getChecker()).isNotNull();
        assertThat(message.getChecker().getLanguage()).isEqualTo("cpp17");
        assertThat(message.getChecker().getSource()).contains("checker");
        assertThat(message.getChecker().getTimeLimit()).isEqualTo(3000);
        assertThat(message.getChecker().getMemoryLimit()).isEqualTo(512);
        assertThat(message.getChecker().getStackLimit()).isEqualTo(256);
        assertThat(message.getChecker().getOutputLimit()).isEqualTo(1048576);
        assertThat(message.getChecker().getProtocol()).isEqualTo("hnieoj-result-json-v1");
        assertThat(message.getChecker().getArgumentTemplate()).isEqualTo("{input} {expected} {actual} {result}");
    }

    @Test
    void shouldWriteInteractiveContractIntoOutboxPayload() throws Exception {
        when(streamService.resolveStreamKey("interactive")).thenReturn("hnieoj:judge:task:interactive");
        ObjectMapper objectMapper = new ObjectMapper();
        RedisJudgeTaskMessagePublisher publisher = newPublisher(objectMapper, new SubmissionProperties());

        publisher.publishAfterCommit(buildJudge(), buildInteractiveProblem());

        JudgeTaskMessage message = capturedMessage(objectMapper, publisher);
        assertThat(message.getJudgeMode()).isEqualTo("interactive");
        assertThat(message.getInteractor()).isNotNull();
        assertThat(message.getInteractor().getProtocol()).isEqualTo("hnieoj-result-json-v1");
        assertThat(message.getInteractor().getArgumentTemplate()).isEqualTo("{input} {expected} {result}");
        assertThat(message.getInteraction()).isNotNull();
        assertThat(message.getInteraction().getProtocol()).isEqualTo("stdio");
        assertThat(message.getInteraction().getWiring()).isEqualTo("bidirectional-stdio");
        assertThat(message.getInteraction().getScoreMode()).isEqualTo("interactor");
    }

    @Test
    void shouldRejectOutboxWhenCheckerSourceTooLarge() {
        when(streamService.resolveStreamKey("spj")).thenReturn("hnieoj:judge:task:spj");
        SubmissionProperties submissionProperties = new SubmissionProperties();
        submissionProperties.setMaxCheckerBytes(4);
        RedisJudgeTaskMessagePublisher publisher = newPublisher(new ObjectMapper(), submissionProperties);

        assertThatThrownBy(() -> publisher.publishAfterCommit(buildJudge(), buildSpjProblem()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("SPJ checker");
        verifyNoInteractions(outboxMapper);
    }

    @Test
    void shouldRejectUnsupportedJudgeMode() {
        RedisJudgeTaskMessagePublisher publisher = newPublisher(new ObjectMapper(), new SubmissionProperties());
        ProblemBasicDto problem = new ProblemBasicDto();
        problem.setJudgeMode("go");

        assertThatThrownBy(() -> publisher.publishAfterCommit(buildJudge(), problem))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的判题模式");
        verifyNoInteractions(outboxMapper);
    }

    private RedisJudgeTaskMessagePublisher newPublisher(ObjectMapper objectMapper,
                                                        SubmissionProperties submissionProperties) {
        return new RedisJudgeTaskMessagePublisher(streamService, new JudgeStreamProperties(), outboxMapper,
                executionMapper, objectMapper, submissionProperties);
    }

    private JudgeTaskMessage capturedMessage(ObjectMapper objectMapper, RedisJudgeTaskMessagePublisher publisher)
            throws Exception {
        ArgumentCaptor<JudgeTaskOutbox> captor = ArgumentCaptor.forClass(JudgeTaskOutbox.class);
        verify(outboxMapper).insert(captor.capture());
        return objectMapper.readValue(captor.getValue().getPayload(), JudgeTaskMessage.class);
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
        problem.setSpjProtocol("hnieoj-result-json-v1");
        return problem;
    }

    private ProblemBasicDto buildInteractiveProblem() {
        ProblemBasicDto problem = new ProblemBasicDto();
        problem.setJudgeMode("interactive");
        problem.setInteractorLanguage("cpp17");
        problem.setInteractorCode("// interactor");
        problem.setInteractorTimeLimit(5000);
        problem.setInteractorMemoryLimit(256);
        problem.setInteractorStackLimit(128);
        problem.setInteractorOutputLimit(16777216);
        problem.setInteractorProtocol("hnieoj-result-json-v1");
        return problem;
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
