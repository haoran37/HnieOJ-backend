package com.hnieacm.achievement.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 成就认证文件服务
 */
public interface AchievementFileService {

    /**
     * 保存文件并返回对外 URL
     */
    String store(String uid, MultipartFile file);
}

