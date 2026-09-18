package com.hnieacm.achievement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 驳回成就认证申请参数
 */
@Data
public class RejectAchievementApplyRequest {

    @NotBlank(message = "reason 不能为空")
    private String reason;
}

