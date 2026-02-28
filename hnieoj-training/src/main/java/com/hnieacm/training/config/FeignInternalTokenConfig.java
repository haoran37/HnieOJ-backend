package com.hnieacm.training.config;

import com.hnieacm.common.constant.HeaderConstant;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: Feign 内部调用统一透传 X-Internal-Token
 */
@Configuration
public class FeignInternalTokenConfig {

    @Value("${hnieoj.internal.token:}")
    private String internalToken;

    @Bean
    public RequestInterceptor internalTokenRequestInterceptor() {
        return template -> {
            if (StringUtils.hasText(internalToken)) {
                template.header(HeaderConstant.INTERNAL_TOKEN, internalToken);
            }
        };
    }
}
