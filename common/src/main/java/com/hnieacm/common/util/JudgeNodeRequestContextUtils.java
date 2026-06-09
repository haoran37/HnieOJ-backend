package com.hnieacm.common.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.hnieacm.common.constant.HeaderConstant;
import com.hnieacm.common.dto.JudgeNodeRequestContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 判题节点请求签名上下文工具
 */
public class JudgeNodeRequestContextUtils {

    private JudgeNodeRequestContextUtils() {
    }

    public static JudgeNodeRequestContext build(HttpServletRequest request, byte[] body) {
        JudgeNodeRequestContext context = new JudgeNodeRequestContext();
        context.setMethod(request.getMethod());
        context.setPathWithQuery(pathWithQuery(request));
        context.setSourceIp(sourceIp(request));
        context.setNodeIdHeader(trim(request.getHeader(HeaderConstant.JUDGE_NODE_ID)));
        context.setTokenIdHeader(trim(request.getHeader(HeaderConstant.JUDGE_TOKEN_ID)));
        context.setInstanceId(trim(request.getHeader(HeaderConstant.JUDGE_INSTANCE_ID)));
        context.setFingerprintHash(trim(request.getHeader(HeaderConstant.JUDGE_FINGERPRINT)));
        context.setSignatureAlgorithm(trim(request.getHeader(HeaderConstant.JUDGE_SIGNATURE_ALGORITHM)));
        context.setTimestamp(trim(request.getHeader(HeaderConstant.JUDGE_TIMESTAMP)));
        context.setNonce(trim(request.getHeader(HeaderConstant.JUDGE_NONCE)));
        context.setBodySha256(trim(request.getHeader(HeaderConstant.JUDGE_BODY_SHA256)));
        context.setActualBodySha256(DigestUtil.sha256Hex(body == null ? new byte[0] : body));
        context.setSignature(trim(request.getHeader(HeaderConstant.JUDGE_SIGNATURE)));
        return context;
    }

    public static String extractBearerToken(String authorizationHeader) {
        String normalizedHeader = trim(authorizationHeader);
        if (normalizedHeader == null) {
            return null;
        }
        String bearerPrefix = "Bearer ";
        if (normalizedHeader.regionMatches(true, 0, bearerPrefix, 0, bearerPrefix.length())) {
            return trim(normalizedHeader.substring(bearerPrefix.length()));
        }
        return null;
    }

    private static String pathWithQuery(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = StrUtil.trimToNull(request.getQueryString());
        return query == null ? uri : uri + "?" + query;
    }

    private static String sourceIp(HttpServletRequest request) {
        String forwardedFor = trim(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = trim(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    private static String trim(String value) {
        return StrUtil.trimToNull(value);
    }
}
