package com.hnieacm.achievement.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 批量处理成就认证申请请求
 */
@Data
public class BatchAchievementApplyRequest {

    @NotEmpty(message = "ids 不能为空")
    private List<Long> ids;
}
