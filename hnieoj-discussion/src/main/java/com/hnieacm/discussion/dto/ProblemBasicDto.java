package com.hnieacm.discussion.dto;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 题目基础信息 DTO（内部调用）
 */
@Data
public class ProblemBasicDto {

    private Long id;

    private String problemCode;

    private String title;

    private Integer auth;

    private Integer type;
}
