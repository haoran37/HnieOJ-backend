package com.hnieacm.submission.service.impl;

import com.hnieacm.submission.constant.JudgeTaskOutboxStatusConstant;
import com.hnieacm.submission.constant.SubmissionStatusConstant;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.mapper.JudgeTaskOutboxMapper;
import com.hnieacm.submission.properties.SubmissionProperties;
import com.hnieacm.submission.vo.JudgeOpsSummaryVo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: Judge operation summary tests.
 */
class JudgeOpsServiceImplTest {

    @Test
    void shouldBuildJudgeOpsSummaryFromOutboxAndSubmissionCounts() {
        JudgeTaskOutboxMapper outboxMapper = mock(JudgeTaskOutboxMapper.class);
        JudgeMapper judgeMapper = mock(JudgeMapper.class);
        SubmissionProperties properties = new SubmissionProperties();
        when(outboxMapper.selectCount(any())).thenReturn(0L, 0L, 100L, 2L, 1L);
        when(judgeMapper.selectCount(any())).thenReturn(4L, 1L, 2L, 3L, 1L, 5L);
        JudgeOpsServiceImpl service = new JudgeOpsServiceImpl(outboxMapper, judgeMapper, properties);

        JudgeOpsSummaryVo summary = service.summary();

        assertThat(summary.getOutboxStatusCounts().get(JudgeTaskOutboxStatusConstant.SENT)).isEqualTo(100L);
        assertThat(summary.getAbnormalOutboxCount()).isEqualTo(3L);
        assertThat(summary.getExhaustedOutboxCount()).isEqualTo(1L);
        assertThat(summary.getJudgingStatusCounts().get(SubmissionStatusConstant.PENDING)).isEqualTo(4L);
        assertThat(summary.getJudgingSubmissionCount()).isEqualTo(7L);
        assertThat(summary.getStalePendingCount()).isEqualTo(3L);
        assertThat(summary.getStaleActiveCount()).isEqualTo(1L);
        assertThat(summary.getLongPendingWarnCount()).isEqualTo(5L);
        assertThat(summary.getHealthy()).isFalse();
        assertThat(summary.getWarnings()).contains(
                "outbox_exhausted",
                "outbox_abnormal",
                "stale_pending_submission",
                "stale_active_submission",
                "long_pending_submission"
        );
    }
}
