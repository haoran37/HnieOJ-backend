package com.hnieacm.submission.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.hnieacm.common.result.Result;
import com.hnieacm.submission.constant.RejudgeTaskStatusConstant;
import com.hnieacm.submission.constant.SubmissionStatusConstant;
import com.hnieacm.submission.dto.ProblemBasicDto;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeCase;
import com.hnieacm.submission.entity.RejudgeTask;
import com.hnieacm.submission.feign.ProblemInternalFeignClient;
import com.hnieacm.submission.mapper.JudgeCaseMapper;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.mapper.RejudgeTaskMapper;
import com.hnieacm.submission.properties.SubmissionProperties;
import com.hnieacm.submission.service.JudgeTaskMessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 重判任务服务测试
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class RejudgeTaskServiceImplTest {

    @Mock
    private RejudgeTaskMapper rejudgeTaskMapper;

    @Mock
    private JudgeMapper judgeMapper;

    @Mock
    private JudgeCaseMapper judgeCaseMapper;

    @Mock
    private ProblemInternalFeignClient problemInternalFeignClient;

    @Mock
    private JudgeTaskMessagePublisher judgeTaskMessagePublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private TransactionStatus transactionStatus;

    private SubmissionProperties submissionProperties;
    private RejudgeTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        initTableInfo(RejudgeTask.class);
        initTableInfo(Judge.class);
        initTableInfo(JudgeCase.class);
        submissionProperties = new SubmissionProperties();
        service = new RejudgeTaskServiceImpl(rejudgeTaskMapper, judgeMapper, judgeCaseMapper,
                problemInternalFeignClient, judgeTaskMessagePublisher, submissionProperties, transactionTemplate);
    }

    @Test
    void shouldSkipTaskWhenLeaseClaimFailed() {
        RejudgeTask task = buildTask(RejudgeTaskStatusConstant.PENDING);
        when(rejudgeTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);
        when(rejudgeTaskMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        service.processPendingTasks();

        verifyNoInteractions(problemInternalFeignClient, judgeMapper, judgeCaseMapper, judgeTaskMessagePublisher);
    }

    @Test
    void shouldRejudgeInTransactionWhenLeaseClaimed() {
        RejudgeTask task = buildTask(RejudgeTaskStatusConstant.PENDING);
        Judge judge = buildJudge();
        ProblemBasicDto problem = buildProblem();
        when(rejudgeTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);
        when(rejudgeTaskMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(rejudgeTaskMapper.selectById(task.getId())).thenReturn(task);
        when(problemInternalFeignClient.getProblemBasic(task.getProblemCode())).thenReturn(Result.success(problem));
        when(judgeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(judge));
        when(judgeMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(transactionStatus);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service.processPendingTasks();

        verify(judgeMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(judgeCaseMapper).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<Judge> judgeCaptor = ArgumentCaptor.forClass(Judge.class);
        verify(judgeTaskMessagePublisher).publishAfterCommit(judgeCaptor.capture(), any(ProblemBasicDto.class));
        assertThat(judgeCaptor.getValue().getJudgeTaskId()).isNotBlank();
        assertThat(judgeCaptor.getValue().getCode()).isEqualTo("int main() { return 0; }");
    }

    @Test
    void shouldNotPublishWhenSubmissionBecomesJudging() {
        RejudgeTask task = buildTask(RejudgeTaskStatusConstant.PENDING);
        Judge judge = buildJudge();
        ProblemBasicDto problem = buildProblem();
        when(rejudgeTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);
        when(rejudgeTaskMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(rejudgeTaskMapper.selectById(task.getId())).thenReturn(task);
        when(problemInternalFeignClient.getProblemBasic(task.getProblemCode())).thenReturn(Result.success(problem));
        when(judgeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(judge));
        when(judgeMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(transactionStatus);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service.processPendingTasks();

        verifyNoInteractions(judgeCaseMapper, judgeTaskMessagePublisher);
    }

    @Test
    void shouldMarkTaskFailedWhenJudgeModeUnsupported() {
        RejudgeTask task = buildTask(RejudgeTaskStatusConstant.PENDING);
        ProblemBasicDto problem = buildProblem();
        problem.setJudgeMode("spj");
        when(rejudgeTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);
        when(rejudgeTaskMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(rejudgeTaskMapper.selectById(task.getId())).thenReturn(task);
        when(problemInternalFeignClient.getProblemBasic(task.getProblemCode())).thenReturn(Result.success(problem));

        service.processPendingTasks();

        verifyNoInteractions(judgeMapper, judgeCaseMapper, judgeTaskMessagePublisher);
    }

    private RejudgeTask buildTask(String status) {
        RejudgeTask task = new RejudgeTask();
        task.setId(1L);
        task.setProblemId(2L);
        task.setProblemCode("P1001");
        task.setStatus(status);
        task.setLastJudgeId(0L);
        return task;
    }

    private Judge buildJudge() {
        Judge judge = new Judge();
        judge.setId(10L);
        judge.setSubmitId("submission-10");
        judge.setProblemId(2L);
        judge.setProblemCode("P1001");
        judge.setUid("20230001");
        judge.setLanguage("cpp");
        judge.setCode("int main() { return 0; }");
        judge.setStatus(SubmissionStatusConstant.ACCEPTED);
        return judge;
    }

    private ProblemBasicDto buildProblem() {
        ProblemBasicDto problem = new ProblemBasicDto();
        problem.setId(2L);
        problem.setProblemCode("P1001");
        problem.setJudgeMode("default");
        problem.setHasTestdata(Boolean.TRUE);
        problem.setTestdataCaseCount(1);
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
