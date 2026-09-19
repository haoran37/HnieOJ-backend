package com.hnieacm.judge.vo;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 管理端核心指标展示对象
 */
@Data
public class DashboardMetricsVo {

    private Long totalUsers;

    private Long dau;

    private Long totalProblems;

    private Long totalSubmissions;
}
