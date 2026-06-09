package com.hnieacm.judge.service.impl;

import com.hnieacm.judge.mapper.DashboardMapper;
import com.hnieacm.judge.service.JudgeNodeOpsService;
import com.hnieacm.judge.vo.DashboardHealthVo;
import com.hnieacm.judge.vo.DashboardMetricsVo;
import com.hnieacm.judge.vo.DashboardStatusCountVo;
import com.hnieacm.judge.vo.JudgeNodeOpsSummaryVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 管理端 dashboard 服务测试
 */
class DashboardServiceImplTest {

    private DashboardMapper dashboardMapper;

    private JudgeNodeOpsService judgeNodeOpsService;

    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        dashboardMapper = mock(DashboardMapper.class);
        judgeNodeOpsService = mock(JudgeNodeOpsService.class);
        service = new DashboardServiceImpl(
                dashboardMapper,
                judgeNodeOpsService,
                mock(DataSource.class),
                mock(StringRedisTemplate.class)
        );
    }

    @Test
    void shouldBuildDashboardMetrics() {
        when(dashboardMapper.countTotalUsers()).thenReturn(100L);
        when(dashboardMapper.countTodayActiveSubmitUsers()).thenReturn(8L);
        when(dashboardMapper.countTotalProblems()).thenReturn(30L);
        when(dashboardMapper.countTotalSubmissions()).thenReturn(200L);

        DashboardMetricsVo vo = service.metrics();

        assertThat(vo.getTotalUsers()).isEqualTo(100L);
        assertThat(vo.getDau()).isEqualTo(8L);
        assertThat(vo.getTotalProblems()).isEqualTo(30L);
        assertThat(vo.getTotalSubmissions()).isEqualTo(200L);
    }

    @Test
    void shouldBuildDashboardHealth() {
        DashboardStatusCountVo accepted = new DashboardStatusCountVo();
        accepted.setStatus(0);
        accepted.setCount(80L);
        JudgeNodeOpsSummaryVo nodeSummary = new JudgeNodeOpsSummaryVo();
        nodeSummary.setHealthy(true);

        when(dashboardMapper.countAcceptedSubmissions()).thenReturn(80L);
        when(dashboardMapper.countTerminalSubmissions()).thenReturn(100L);
        when(dashboardMapper.countJudgingSubmissions()).thenReturn(3L);
        when(dashboardMapper.countSystemErrorSubmissions()).thenReturn(1L);
        when(dashboardMapper.averageJudgeTime()).thenReturn(36.5D);
        when(dashboardMapper.listJudgeStatusCounts()).thenReturn(List.of(accepted));
        when(judgeNodeOpsService.summary()).thenReturn(nodeSummary);

        DashboardHealthVo vo = service.health();

        assertThat(vo.getHealthy()).isTrue();
        assertThat(vo.getAcceptedRate()).isEqualTo(0.8D);
        assertThat(vo.getAverageJudgeTime()).isEqualTo(36.5D);
        assertThat(vo.getStatusCounts().get(0).getStatusText()).isEqualTo("Accepted");
    }
}
