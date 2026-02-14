package com.hnieacm.auth.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 认证服务缓存 TTL 配置
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "hnieoj.auth-cache.ttl")
public class AuthCacheTtlProperties {

    /**
     * TTL for role cache (key: auth:roles:{uid}).
     */
    private Duration roles = Duration.ofHours(24);

    /**
     * TTL for permission cache (key: auth:permissions:{uid}).
     */
    private Duration permissions = Duration.ofHours(12);

    /**
     * TTL for empty cache (avoid long negative caching).
     */
    private Duration empty = Duration.ofMinutes(5);
}

