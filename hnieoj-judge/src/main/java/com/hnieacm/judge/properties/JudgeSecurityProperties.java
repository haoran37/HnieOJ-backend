package com.hnieacm.judge.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点安全配置
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "hnieoj.judge.security")
public class JudgeSecurityProperties {
    private String jwtSecret;

    private long tempTokenTtlSeconds = 7200;

    private long authCodeTtlSeconds = 1800;

    private int authCodeMaxExchange = 1;

    private long nodeActiveTimeoutSeconds = 90;
}
