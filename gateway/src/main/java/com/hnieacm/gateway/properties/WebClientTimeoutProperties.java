package com.hnieacm.gateway.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: Gateway WebClient 超时配置
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "hnieoj.gateway.web-client")
public class WebClientTimeoutProperties {

    private int connectTimeoutMs = 3000;

    private int readTimeoutSeconds = 10;

    private int writeTimeoutSeconds = 10;

    private int responseTimeoutSeconds = 10;
}

