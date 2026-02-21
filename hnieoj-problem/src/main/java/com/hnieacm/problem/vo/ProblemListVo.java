package com.hnieacm.problem.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 公共题目列表项
 */
@Data
public class ProblemListVo {

    private Long id;

    private String problemCode;

    private String title;

    private Integer difficulty;

    private List<String> tags;

    private Integer submissionCount;

    private Integer acceptedCount;

    private BigDecimal scorePercentage;
}
