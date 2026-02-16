package com.hnieacm.achievement.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 用户成就展示对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAchievementVo {

    private Long id;

    private String title;

    private String content;

    private String proofUrl;

    /**
     * 成就获得时间（毫秒时间戳）
     */
    private Long achieveTime;

    /**
     * 兼容旧字段：date = achieveTime
     */
    private Long date;

    private Integer status;
}

