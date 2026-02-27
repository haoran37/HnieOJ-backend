package com.hnieacm.user.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.constant.HeaderConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 保护 /internal/** 接口，仅允许携带正确 X-Internal-Token 的请求访问
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
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (internalToken == null || internalToken.isBlank()) {
            log.error("Internal token not configured, reject internal api access, uri: {}", request.getRequestURI());
            writeForbidden(response, "内部调用令牌未配置");
            return false;
        }

        String requestToken = request.getHeader(HeaderConstant.INTERNAL_TOKEN);
        if (internalToken.equals(requestToken)) {
            return true;
        }

        log.warn("Blocked external access to internal api, uri: {}", request.getRequestURI());
        writeForbidden(response, "禁止访问内部接口");
        return false;
    }

    private void writeForbidden(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(ResultCode.FORBIDDEN, msg)));
    }
}
