package com.hnieacm.training.feign.dto;

import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 批量查询题目基础信息请求参数
 */
@Data
public class ProblemBatchQueryRequest {

    private List<Long> ids;
}
