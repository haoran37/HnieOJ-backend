package com.hnieacm.judge.config;

import com.hnieacm.judge.interceptor.InternalApiInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: WebMVC 配置
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final InternalApiInterceptor internalApiInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalApiInterceptor).addPathPatterns("/internal/**");
    }
}
