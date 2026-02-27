package com.hnieacm.contest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 比赛题目请求参数
 */
@Data
public class AdminContestProblemRequest {

    @NotNull(message = "problemId 不能为空")
    @Min(value = 1, message = "problemId 必须大于 0")
    private Long problemId;

    private String displayId;

    private String displayTitle;

    private String color;
}
