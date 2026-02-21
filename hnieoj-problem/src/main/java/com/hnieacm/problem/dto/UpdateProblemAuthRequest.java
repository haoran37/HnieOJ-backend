package com.hnieacm.problem.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 切换题目公开状态请求
 */
@Data
public class UpdateProblemAuthRequest {

    @NotNull(message = "pid不能为空")
    private Long pid;

    @NotNull(message = "auth不能为空")
    private Integer auth;
}
