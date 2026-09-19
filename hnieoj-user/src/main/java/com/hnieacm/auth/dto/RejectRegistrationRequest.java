package com.hnieacm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 驳回注册申请参数
 */
@Data
public class RejectRegistrationRequest {

    @NotBlank(message = "reason 不能为空")
    private String reason;
}

