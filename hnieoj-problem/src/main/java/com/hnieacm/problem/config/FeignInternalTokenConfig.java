package com.hnieacm.problem.config;

import com.hnieacm.common.constant.HeaderConstant;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/20
 * @Description: 为内部 Feign 调用添加 X-Internal-Token
 */
@Configuration
public class FeignInternalTokenConfig {

    @Value("${hnieoj.internal.token:}")
    private String internalToken;

    /**
     * @MethodName internalTokenRequestInterceptor
     *
     * @Description 内部令牌请求拦截器
     * @Return @return {@link RequestInterceptor }
     * @Author HaoRan_Lyu
     * @Date 2026/02/20
     */
    @Bean
    public RequestInterceptor internalTokenRequestInterceptor() {
        return template -> {
            if (StringUtils.hasText(internalToken)) {
                template.header(HeaderConstant.INTERNAL_TOKEN, internalToken);
            }
        };
    }
}

