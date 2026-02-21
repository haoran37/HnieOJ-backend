package com.hnieacm.problem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 题目样例请求项
 */
@Data
public class ProblemExampleRequest {

    @NotBlank(message = "example.input不能为空")
    private String input;

    @NotBlank(message = "example.output不能为空")
    private String output;
}
