package com.hnieacm.achievement.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/17
 * @Description: 成就认证申请列表项（管理员）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AchievementApplyAdminVo {

    private Long id;

    private String uid;

    private String username;

    private String title;

    private String status;

    /**
     * 提交时间（毫秒级时间戳）
     */
    private Long submitTime;
}

