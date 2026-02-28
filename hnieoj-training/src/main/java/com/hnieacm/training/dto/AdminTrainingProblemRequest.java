package com.hnieacm.training.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 管理端题单题目请求参数
 */
@Data
public class AdminTrainingProblemRequest {

    @NotNull(message = "problemId 不能为空")
    @Min(value = 1, message = "problemId 必须大于 0")
    private Long problemId;

    @Min(value = 1, message = "displayId 必须大于 0")
    private Integer displayId;
}
