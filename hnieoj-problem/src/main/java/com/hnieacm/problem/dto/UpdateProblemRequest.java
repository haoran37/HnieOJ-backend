package com.hnieacm.problem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 编辑题目请求
 */
@Data
public class UpdateProblemRequest {

    @Valid
    @NotNull(message = "problem不能为空")
    private ProblemRequest problem;

    private List<String> tags;
}
