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
     * 成就标题（建议传入；若不传将默认使用 content 截断生成）
     * TODO: 后期调整
     */
    private String title;

    @NotBlank(message = "content 不能为空")
    private String content;

    /**
     * 成就证明材料 URL（可选）
     */
    private String proofUrl;

    private Long achieveTime;
}
