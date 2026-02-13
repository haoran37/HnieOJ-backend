package com.hnieacm.auth.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.constant.HeaderConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;


/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: /internal/** 接口拦截器：仅允许内部服务调用。当未配置 hnieoj.internal.token 时，会拦截并返回 403，避免 internal 接口被误暴露。需在请求携带 X-Internal-Token
 */
@Slf4j
@Component
@RefreshScope
@RequiredArgsConstructor
public class InternalApiInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Value("${hnieoj.internal.token:}")
    private String internalToken;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        if (internalToken == null || internalToken.isBlank()) {
            log.error("Internal token not configured, reject internal api access, uri: {}", request.getRequestURI());
            log.error("缺少内部调用凭证，请在请求头携带 " + HeaderConstant.INTERNAL_TOKEN);
            return false;
        }

        String requestToken = request.getHeader(HeaderConstant.INTERNAL_TOKEN);
        if (internalToken.equals(requestToken)) {
            return true;
        }

        log.warn("Blocked external access to internal api, uri: {}", request.getRequestURI());
        // log.error("禁止访问内部接口");
        return false;
    }
}
