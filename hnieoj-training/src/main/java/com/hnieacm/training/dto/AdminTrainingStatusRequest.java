package com.hnieacm.training.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 管理端切换题单状态请求参数
 */
@Data
public class AdminTrainingStatusRequest {

    @NotNull(message = "id 不能为空")
    @Min(value = 1, message = "id 必须大于 0")
    private Long id;

    @NotNull(message = "status 不能为空")
    private Boolean status;
}
