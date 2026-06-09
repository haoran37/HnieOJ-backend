package com.hnieacm.judge.vo;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 热门题目展示对象
 */
@Data
public class PopularProblemVo {

    private Long id;

    private String problemCode;

    private String title;

    private Integer submissionCount;

    private Integer acceptedCount;
}
