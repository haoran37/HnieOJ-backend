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

    /**
     * 按申请记录中的本地存储 key 读取附件，规范化后必须落在 upload-dir 以内
     */
    LocalFile loadLocal(String storedValue);

    /**
     * 本地附件内容与安全文件名
     */
    record LocalFile(byte[] content, String filename) {
    }
}

