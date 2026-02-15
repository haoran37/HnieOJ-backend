package com.hnieacm.auth.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 认证模块校验参数
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "hnieoj.auth.validation")
public class AuthValidationProperties {

    private int usernameMinLength = 2;

    private int usernameMaxLength = 20;

    private int passwordMinLength = 6;

    private int passwordMaxLength = 32;
}

