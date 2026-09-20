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
     * 申请说明（原始 description）
     */
    private String description;

    /**
     * 附件地址：本地存储时输出受保护的下载接口，外部存储时输出原 HTTP(S) 地址
     */
    private String fileUrl;

    /**
     * 提交时间（毫秒级时间戳）
     */
    private Long submitTime;
}

