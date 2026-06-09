package com.hnieacm.judge.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 管理端系统健康度展示对象
 */
@Data
public class DashboardHealthVo {

    private Boolean healthy;

    private Double acceptedRate;

    private Double averageJudgeTime;

    private Long judgingSubmissionCount;

    private Long systemErrorCount;

    private List<DashboardStatusCountVo> statusCounts;

    private JudgeNodeOpsSummaryVo judgeNodeSummary;
}
