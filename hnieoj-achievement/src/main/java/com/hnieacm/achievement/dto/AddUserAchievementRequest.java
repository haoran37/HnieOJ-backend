package com.hnieacm.achievement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 添加用户成就参数
 */
@Data
public class AddUserAchievementRequest {

    /**
     * 成就标题。
     */
    @NotBlank(message = "title 不能为空")
    private String title;

    @NotBlank(message = "content 不能为空")
    private String content;

    /**
     * 成就证明材料 URL（可选）
     */
    private String proofUrl;

    private Long achieveTime;
}
