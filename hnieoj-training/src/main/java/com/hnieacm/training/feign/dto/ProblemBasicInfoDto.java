package com.hnieacm.training.feign.dto;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 题目基础信息
 */
@Data
public class ProblemBasicInfoDto {

    private Long id;

    private String problemCode;

    private String title;
}
