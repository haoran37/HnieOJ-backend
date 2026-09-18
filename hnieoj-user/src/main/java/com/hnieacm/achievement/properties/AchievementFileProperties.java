package com.hnieacm.achievement.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 成就认证文件存储相关配置
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "hnieoj.achievement.file")
public class AchievementFileProperties {

    /**
     * 文件上传落盘目录
     * TODO：通过 Nacos 配置到独立挂载盘
     */
    private String uploadDir;

    /**
     * 对外可访问的 URL 前缀
     * TODO：混淆文件名，避免文件名被猜到
     */
    private String publicUrlPrefix;
}

