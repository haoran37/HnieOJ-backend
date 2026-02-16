package com.hnieacm.achievement.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 成就认证申请服务
 */
public interface AchievementApplyService {

    /**
     * 提交成就认证申请
     */
    void submitApply(String loginUid, String title, String description, MultipartFile file);

    /**
     * 通过成就认证申请
     */
    void approve(Long id);

    /**
     * 驳回成就认证申请
     */
    void reject(Long id, String reason);
}

