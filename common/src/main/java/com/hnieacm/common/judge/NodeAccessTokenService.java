package com.hnieacm.common.judge;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * 短期 NODE_ACCESS 令牌签发与校验（HMAC-SHA256）。
 *
 * <p>令牌绑定 nodeId/keyId/公钥指纹/accessVersion/sessionEpoch，受众为配置值。
 * 令牌有效期必须被节点硬截止（authorizationUntil / key grace）进一步封顶。
 * 本类不记录令牌明文日志。</p>
 *
 * @author Codex
 */
public class NodeAccessTokenService {

    public static final String CLAIM_TYPE = "typ";
    public static final String CLAIM_AUDIENCE = "aud";
    public static final String CLAIM_SUBJECT = "sub";
    public static final String CLAIM_KEY_ID = "kid";
    public static final String CLAIM_KEY_THUMBPRINT = "ktp";
    public static final String CLAIM_ACCESS_VERSION = "av";
    public static final String CLAIM_SESSION_EPOCH = "se";
    public static final String CLAIM_SCOPE = "scope";
    public static final String CLAIM_JWT_ID = "jti";

    /** 固定签名算法：任何 alg=none / 非 HS256 头部一律拒绝，绝不由客户端指定算法。 */
    private static final String REQUIRED_ALGORITHM = "HS256";

    /** 允许的签发时间时钟偏差，防止未来 iat 令牌长期有效。 */
    private static final long MAX_IAT_SKEW_MILLIS = 60_000L;

    /** 秒级时间戳上限：乘以 1000 后仍不溢出 long。 */
    private static final long MAX_EPOCH_SECONDS = Long.MAX_VALUE / 1000L;

    private final byte[] secret;
    private final String audience;
    private final long ttlSeconds;

    public NodeAccessTokenService(String secret, String audience, long ttlSeconds) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("node access token secret must be at least 32 characters");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("node access token audience must not be blank");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.audience = audience;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * 签发短期令牌。
     *
     * @param nodeId         节点 registryID
     * @param keyId          绑定密钥 ID
     * @param keyThumbprint  公钥 SHA-256 摘要
     * @param accessVersion  节点访问版本
     * @param sessionEpoch   当前会话纪元
     * @param hardDeadlineMs 节点/密钥硬截止毫秒时间戳，可为 {@link Long#MAX_VALUE}
     * @return 令牌明文与过期毫秒时间戳
     */
    public IssuedToken issue(String nodeId, String keyId, String keyThumbprint, int accessVersion,
                             long sessionEpoch, long hardDeadlineMs) {
        long now = System.currentTimeMillis();
        long desired = now + ttlSeconds * 1000L;
        long expiresAt = Math.min(desired, hardDeadlineMs);
        if (expiresAt <= now) {
            throw new IllegalStateException("node authorization already expired, cannot issue access token");
        }
        JWT jwt = JWT.create()
                .setPayload(CLAIM_TYPE, NodeProtocolConstants.TOKEN_TYPE_NODE_ACCESS)
                .setPayload(CLAIM_AUDIENCE, audience)
                .setPayload(CLAIM_SUBJECT, nodeId)
                .setPayload(CLAIM_KEY_ID, keyId)
                .setPayload(CLAIM_KEY_THUMBPRINT, keyThumbprint)
                .setPayload(CLAIM_ACCESS_VERSION, accessVersion)
                .setPayload(CLAIM_SESSION_EPOCH, sessionEpoch)
                .setPayload(CLAIM_SCOPE, NodeProtocolConstants.TOKEN_SCOPE_NODE_TASK)
                .setPayload(CLAIM_JWT_ID, UUID.randomUUID().toString())
                .setIssuedAt(new Date(now))
                .setExpiresAt(new Date(expiresAt));
        return new IssuedToken(jwt.setKey(secret).sign(), expiresAt);
    }

    /**
     * 校验并解析令牌；任何不合法均抛出统一的未授权异常。
     *
     * <p>安全要点：算法固定为 HS256，绝不由 JWT 头部驱动校验算法，
     * 因此 {@code alg=none} 或任何非 HS256 头部一律拒绝；随后逐项校验 claims
     * 的存在性、类型与取值范围，缺失或类型错误同样拒绝。</p>
     */
    public ParsedToken parse(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("missing node access token");
        }
        JWT jwt;
        try {
            jwt = JWTUtil.parseToken(token);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed node access token");
        }
        Object algorithm = jwt.getHeader("alg");
        if (!REQUIRED_ALGORITHM.equals(algorithm)) {
            throw new IllegalArgumentException("node access token algorithm not allowed");
        }
        JWTSigner signer = JWTSignerUtil.hs256(secret);
        if (!jwt.verify(signer)) {
            throw new IllegalArgumentException("node access token signature mismatch");
        }
        if (!NodeProtocolConstants.TOKEN_TYPE_NODE_ACCESS.equals(requireString(jwt, CLAIM_TYPE))) {
            throw new IllegalArgumentException("unexpected token type");
        }
        if (!audience.equals(requireString(jwt, CLAIM_AUDIENCE))) {
            throw new IllegalArgumentException("node access token audience mismatch");
        }
        if (!NodeProtocolConstants.TOKEN_SCOPE_NODE_TASK.equals(requireString(jwt, CLAIM_SCOPE))) {
            throw new IllegalArgumentException("node access token scope mismatch");
        }
        String nodeId = requireString(jwt, CLAIM_SUBJECT);
        String keyId = requireString(jwt, CLAIM_KEY_ID);
        String keyThumbprint = requireString(jwt, CLAIM_KEY_THUMBPRINT);
        String jwtId = requireString(jwt, CLAIM_JWT_ID);
        long accessVersion = requireNumber(jwt, CLAIM_ACCESS_VERSION);
        if (accessVersion < 0 || accessVersion > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("node access token accessVersion out of range");
        }
        long sessionEpoch = requireNumber(jwt, CLAIM_SESSION_EPOCH);
        if (sessionEpoch <= 0) {
            throw new IllegalArgumentException("node access token sessionEpoch must be positive");
        }
        long issuedAt = requireTimestampMillis(requireNumber(jwt, "iat"), "iat");
        long expiresAt = requireTimestampMillis(requireNumber(jwt, "exp"), "exp");
        long now = System.currentTimeMillis();
        if (issuedAt > now + MAX_IAT_SKEW_MILLIS) {
            throw new IllegalArgumentException("node access token issued in the future");
        }
        if (expiresAt <= now) {
            throw new IllegalArgumentException("node access token expired");
        }
        if (expiresAt <= issuedAt) {
            throw new IllegalArgumentException("node access token expiry before issue time");
        }
        return new ParsedToken(nodeId, keyId, keyThumbprint, (int) accessVersion,
                sessionEpoch, expiresAt, jwtId, Map.of());
    }

    public String getAudience() {
        return audience;
    }

    private static String requireString(JWT jwt, String claim) {
        Object value = jwt.getPayload(claim);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("missing or invalid node access token claim: " + claim);
        }
        return text;
    }

    /**
     * 严格读取整数 claim：拒绝小数/浮点（含 {@code 1.5}）与字符串，避免 {@code longValue()}
     * 静默截断分数；超出 long 精确表示范围的数值同样因整性校验失败而被拒绝。
     *
     * <p>精确性要求：{@link BigDecimal}/{@link BigInteger} 必须按精确值判定，绝不能用
     * {@code doubleValue()} 比较——{@code 1.0000000000000000001} 会被舍入成 {@code 1.0}
     * 而误判为整数。浮点类型仅接受可被 double 精确表示的整数值。</p>
     */
    private static long requireNumber(JWT jwt, String claim) {
        Object value = jwt.getPayload(claim);
        if (value instanceof BigDecimal decimal) {
            return exactLong(decimal, claim);
        }
        if (value instanceof BigInteger bigInteger) {
            try {
                return bigInteger.longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("node access token claim out of range: " + claim);
            }
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof Number number) {
            // 其他 Number 实现（含 Float/Double）一律先转成十进制字符串再精确取整，
            // 避免 doubleValue 舍入把 1.0000000000000000001 误判为 1。
            try {
                return exactLong(new BigDecimal(number.toString()), claim);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("node access token claim must be integral: " + claim);
            }
        }
        throw new IllegalArgumentException("missing or invalid node access token claim: " + claim);
    }

    /**
     * 精确转为 long：非整值或超出 long 范围一律拒绝。
     *
     * @param decimal BigDecimal 数值
     * @param claim   claim 名称，仅用于异常信息
     * @return 精确 long 值
     */
    private static long exactLong(BigDecimal decimal, String claim) {
        try {
            return decimal.longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("node access token claim must be integral: " + claim);
        }
    }

    /**
     * 校验秒级时间戳在乘以 1000 前是正数且位于安全范围，避免溢出后落到“合法未来时间”。
     *
     * @param seconds 秒级时间戳
     * @param claim   claim 名称，仅用于异常信息
     * @return 毫秒级时间戳
     */
    private static long requireTimestampMillis(long seconds, String claim) {
        if (seconds <= 0 || seconds > MAX_EPOCH_SECONDS) {
            throw new IllegalArgumentException("node access token claim out of range: " + claim);
        }
        return seconds * 1000L;
    }

    /**
     * 签发结果。
     *
     * @param token     令牌明文
     * @param expiresAt 过期毫秒时间戳
     */
    public record IssuedToken(String token, long expiresAt) {
    }

    /**
     * 解析后的可信令牌声明。
     */
    public record ParsedToken(String nodeId, String keyId, String keyThumbprint, int accessVersion,
                              long sessionEpoch, long expiresAt, String jwtId, Map<String, Object> extra) {
    }
}
