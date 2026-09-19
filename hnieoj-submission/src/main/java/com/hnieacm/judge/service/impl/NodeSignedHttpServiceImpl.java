package com.hnieacm.judge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.common.dto.JudgeTaskAccessRequest;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.judge.NodeAccessTokenService;
import com.hnieacm.common.judge.NodeProtocolConstants;
import com.hnieacm.common.judge.NodeSignatureCodec;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.entity.JudgeNodeKey;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.properties.NodeSecurityProperties;
import com.hnieacm.judge.service.NodeIdentityService;
import com.hnieacm.judge.service.NodeSignedHttpService;
import com.hnieacm.judge.service.SignedCaller;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

/**
 * 节点 HTTP 签名校验实现。
 *
 * <p>令牌、签名头、数据库节点/密钥状态与 accessVersion/sessionEpoch 必须全部一致，
 * 仅凭令牌或仅凭签名头都不足以通过；nonce 只消费一次。</p>
 *
 * @author Codex
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeSignedHttpServiceImpl implements NodeSignedHttpService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_NONCE_LENGTH = 128;
    private static final int MAX_NODE_ID_LENGTH = 64;
    private static final int MAX_KEY_ID_LENGTH = 64;
    private static final int MAX_METHOD_LENGTH = 16;
    private static final int MAX_PATH_LENGTH = 1024;
    private static final int MAX_SIGNATURE_LENGTH = 1024;
    private static final int MAX_BODY_HASH_LENGTH = 64;
    private static final int MAX_TIMESTAMP_LENGTH = 20;

    private final NodeIdentityService nodeIdentityService;
    private final NodeSecurityProperties nodeSecurityProperties;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public SignedCaller authorize(HttpServletRequest request, byte[] body) {
        String token = extractBearer(request.getHeader("Authorization"));
        String nodeId = request.getHeader(NodeProtocolConstants.HEADER_NODE_ID);
        String keyId = request.getHeader(NodeProtocolConstants.HEADER_KEY_ID);
        String timestamp = request.getHeader(NodeProtocolConstants.HEADER_TIMESTAMP);
        String nonce = request.getHeader(NodeProtocolConstants.HEADER_NONCE);
        String signature = request.getHeader(NodeProtocolConstants.HEADER_SIGNATURE);
        String method = request.getMethod().toUpperCase();
        String pathWithQuery = rawPathWithQuery(request);
        String bodyHash = NodeSignatureCodec.sha256Hex(body == null ? new byte[0] : body);
        return verify(token, nodeId, keyId, timestamp, nonce, signature, method, pathWithQuery, bodyHash);
    }

    @Override
    public SignedCaller authorizeContext(JudgeTaskAccessRequest access) {
        if (access == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "任务访问上下文不能为空");
        }
        if (StrUtil.hasBlank(access.getMethod(), access.getPathWithQuery(), access.getBodySha256())) {
            throw new BizException(ResultCode.BAD_REQUEST, "任务访问上下文缺少方法/路径/体摘要");
        }
        // 内部调用同样有界校验（不假设上游一定经过 Bean Validation）。
        requireMaxLength(access.getNodeId(), MAX_NODE_ID_LENGTH, "nodeId");
        requireMaxLength(access.getKeyId(), MAX_KEY_ID_LENGTH, "keyId");
        requireMaxLength(access.getMethod(), MAX_METHOD_LENGTH, "method");
        requireMaxLength(access.getPathWithQuery(), MAX_PATH_LENGTH, "pathWithQuery");
        requireMaxLength(access.getSignature(), MAX_SIGNATURE_LENGTH, "signature");
        requireMaxLength(access.getTimestamp(), MAX_TIMESTAMP_LENGTH, "timestamp");
        requireMaxLength(access.getNonce(), MAX_NONCE_LENGTH, "nonce");
        if (access.getBodySha256().length() != MAX_BODY_HASH_LENGTH
                || !access.getBodySha256().matches("[0-9a-fA-F]{64}")) {
            throw new BizException(ResultCode.BAD_REQUEST, "bodySha256 必须为 64 位十六进制");
        }
        String token = extractBearer(access.getAuthorization());
        return verify(token, access.getNodeId(), access.getKeyId(), access.getTimestamp(),
                access.getNonce(), access.getSignature(), access.getMethod().toUpperCase(),
                access.getPathWithQuery(), access.getBodySha256());
    }

    private void requireMaxLength(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new BizException(ResultCode.BAD_REQUEST, field + " 过长");
        }
    }

    private SignedCaller verify(String token, String nodeId, String keyId, String timestamp,
                                String nonce, String signature, String method, String pathWithQuery, String bodyHash) {
        NodeAccessTokenService.ParsedToken parsed = nodeIdentityService.parseAccessToken(token);
        if (StrUtil.hasBlank(nodeId, keyId, timestamp, nonce, signature)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "缺少节点签名头");
        }
        if (nonce.length() > MAX_NONCE_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "nonce 过长");
        }
        if (!Objects.equals(parsed.nodeId(), nodeId) || !Objects.equals(parsed.keyId(), keyId)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "令牌与签名主体不一致");
        }
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "时间戳格式非法");
        }
        long skewMillis = nodeSecurityProperties.getAllowedClockSkewSeconds() * 1000L;
        if (Math.abs(System.currentTimeMillis() - requestTime) > skewMillis) {
            throw new BizException(ResultCode.UNAUTHORIZED, "请求时间戳超出允许偏差");
        }
        // 数据库权威：节点状态/硬截止/密钥状态/sessionEpoch/accessVersion 必须与令牌一致。
        JudgeNodeToken node = nodeIdentityService.requireSessionOwner(nodeId, keyId,
                parsed.sessionEpoch(), parsed.accessVersion(), true);
        JudgeNodeKey key = nodeIdentityService.requireUsableKey(nodeId, keyId, true);
        // 令牌绑定的公钥指纹必须等于当前数据库密钥指纹，防止换 key 重放。
        if (!Objects.equals(parsed.keyThumbprint(), key.getPublicKeyHash())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "令牌与当前公钥不匹配");
        }
        if (node.getActiveKeyId() != null && !Objects.equals(node.getActiveKeyId(), keyId)
                && !NodeProtocolConstants.KEY_STATUS_GRACE.equals(key.getStatus())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "令牌密钥并非当前激活密钥");
        }
        String audience = nodeSecurityProperties.getAudience();
        boolean valid = NodeSignatureCodec.verify(key.getPublicKey(), NodeSignatureCodec.canonical(
                NodeProtocolConstants.DOMAIN_HTTP, audience, nodeId, keyId, method, pathWithQuery,
                bodyHash, timestamp, nonce), signature);
        if (!valid) {
            throw new BizException(ResultCode.UNAUTHORIZED, "HTTP 签名校验失败");
        }
        consumeNonce(nodeId, nonce);
        return new SignedCaller(nodeId, keyId, parsed.accessVersion(), parsed.sessionEpoch());
    }

    private void consumeNonce(String nodeId, String nonce) {
        String key = NodeProtocolConstants.REDIS_HTTP_NONCE_PREFIX + nodeId + ":" + nonce;
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(key, "1",
                Duration.ofSeconds(nodeSecurityProperties.getNonceTtlSeconds()));
        if (!Boolean.TRUE.equals(first)) {
            throw new BizException(ResultCode.FORBIDDEN, "签名 nonce 已被使用");
        }
    }

    private String rawPathWithQuery(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return query == null || query.isEmpty() ? uri : uri + "?" + query;
    }

    private String extractBearer(String authorization) {
        if (StrUtil.isBlank(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "缺少 NODE_ACCESS 令牌");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BizException(ResultCode.UNAUTHORIZED, "缺少 NODE_ACCESS 令牌");
        }
        return token;
    }
}
