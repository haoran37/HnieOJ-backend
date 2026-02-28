package com.hnieacm.training.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 管理端作业题目请求参数
 */
@Data
public class AdminHomeworkProblemRequest {

    @NotNull(message = "problemId 不能为空")
    @Min(value = 1, message = "problemId 必须大于等于 1")
    private Long problemId;

    @Pattern(regexp = "^[A-Za-z]+$", message = "displayId 仅支持字母序号，如 A、B、AA")
    private String displayId;
}
