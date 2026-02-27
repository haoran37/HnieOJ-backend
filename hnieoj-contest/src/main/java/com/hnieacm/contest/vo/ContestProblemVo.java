package com.hnieacm.contest.vo;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 比赛题目展示对象
 */
@Data
public class ContestProblemVo {

    private Long id;

    private Long problemId;

    private String displayId;

    private String displayTitle;

    private String color;
}
