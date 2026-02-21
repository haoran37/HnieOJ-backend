package com.hnieacm.problem.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/20
 * @Description: 删除题目请求
 */
@Data
public class DeleteProblemRequest {

    @NotNull(message = "pid不能为空")
    private Long pid;
}
