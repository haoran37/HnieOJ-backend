package com.hnieacm.problem.vo;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 题目存在性检查展示对象
 */
@Data
public class ProblemCheckVo {

    private Boolean exists;

    private Long problemId;

    private String problemCode;

    private String title;
}
