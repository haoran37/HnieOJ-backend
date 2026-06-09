package com.hnieacm.judge.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 管理端热门内容展示对象
 */
@Data
public class DashboardContentVo {

    private List<PopularProblemVo> popularProblems;

    private List<ActiveTrainingVo> activeTrainings;
}
