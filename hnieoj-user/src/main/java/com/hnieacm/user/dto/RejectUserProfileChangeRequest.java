package com.hnieacm.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 驳回用户资料修改申请请求
 */
@Data
public class RejectUserProfileChangeRequest {

    @NotBlank(message = "reason 不能为空")
    private String reason;
}
