package com.hnieacm.submission.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hnieacm.submission.constant.SubmissionStatusConstant;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeTaskOutbox;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.mapper.JudgeTaskOutboxMapper;
import com.hnieacm.submission.properties.SubmissionProperties;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: 提交判题超时扫描测试
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class SubmissionJudgeTimeoutScannerTest {

    @Mock
    private JudgeMapper judgeMapper;

    @Mock
    private JudgeTaskOutboxMapper outboxMapper;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private SubmissionJudgeTimeoutScanner scanner;

    @BeforeEach
    void setUp() {
        initTableInfo(Judge.class);
        initTableInfo(JudgeTaskOutbox.class);
        scanner = new SubmissionJudgeTimeoutScanner(
                judgeMapper, outboxMapper, new SubmissionProperties(), messagingTemplate);
    }

    @Test
    void shouldNotMarkPendingSystemErrorWhenTaskAlreadySent() {
        Judge judge = new Judge();
        judge.setId(1L);
        judge.setSubmitId("submission-1");
        judge.setJudgeTaskId("task-1");
        judge.setStatus(SubmissionStatusConstant.PENDING);
        judge.setGmtModified(LocalDateTime.now().minusHours(2));
        when(judgeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(judge), List.of(), List.of());
        when(outboxMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L, 0L);

        scanner.scanTimeoutSubmissions();

        verify(judgeMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
