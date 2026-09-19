package com.hnieacm.judge.service;

import com.hnieacm.common.dto.JudgeTaskAccessRequest;
import jakarta.servlet.http.HttpServletRequest;

/**
 * NODE_ACCESS 令牌 + Ed25519 HTTP 签名联合校验。
 *
 * <p>签名覆盖原始路径与请求体，禁止重新序列化 JSON；nonce 在 Redis 单次消费。</p>
 *
 * @author Codex
 */
public interface NodeSignedHttpService {

    /**
     * 校验令牌与签名，返回调用方身份。
     *
     * @param request     原始请求（提供方法/路径/查询串/请求头）
     * @param body        实际接收到的请求体原始字节
     * @return 调用方 nodeId/keyId
     */
    SignedCaller authorize(HttpServletRequest request, byte[] body);

    /**
     * 校验由 problem 服务原样透传的签名上下文（内部授权 Feign 调用），返回调用方身份。
     *
     * @param access 原始方法/路径/体摘要/签名头/令牌
     * @return 调用方 nodeId/keyId
     */
    SignedCaller authorizeContext(JudgeTaskAccessRequest access);
}
