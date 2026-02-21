package com.hnieacm.submission.config;

import com.hnieacm.common.constant.HeaderConstant;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 将 AUTHORIZATION 传递给同一请求链中的下游服务
 */
@Configuration
public class FeignAuthHeaderRelayConfig {

    /**
     * @MethodName authorizationRelayInterceptor
     * <p>
     * @Description 授权中继拦截器
     * @Return @return {@link RequestInterceptor }
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    @Bean
    public RequestInterceptor authorizationRelayInterceptor() {
        return template -> {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return;
            }
            HttpServletRequest request = attrs.getRequest();
            String authorization = request.getHeader(HeaderConstant.AUTHORIZATION);
            if (authorization != null && !authorization.isBlank()) {
                template.header(HeaderConstant.AUTHORIZATION, authorization);
            }
        };
    }
}

