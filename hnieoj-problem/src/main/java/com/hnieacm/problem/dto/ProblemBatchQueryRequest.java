package com.hnieacm.problem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 批量查询题目基础信息请求
 */
@Data
public class ProblemBatchQueryRequest {

    @NotEmpty(message = "ids 不能为空")
    private List<@NotNull(message = "problemId 不能为空") @Min(value = 1, message = "problemId 必须大于 0") Long> ids;
}
