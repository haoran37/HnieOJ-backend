package com.hnieacm.common.judge;

import cn.hutool.jwt.JWT;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 短期 NODE_ACCESS 令牌签发/校验测试。
 *
 * @author Codex
 */
class NodeAccessTokenServiceTest {

    /** 每次测试运行随机生成的非生产秘密，避免仓库中出现可被扫描命中的固定密钥字面量。 */
    private static final String SECRET = UUID.randomUUID().toString().replace("-", "");
    private static final String AUDIENCE = "judge.example.test";

    @Test
    void issuesTokenBearingIdentityAndVersion() {
        NodeAccessTokenService service = new NodeAccessTokenService(SECRET, AUDIENCE, 900);
        NodeAccessTokenService.IssuedToken issued = service.issue("node-1", "key-1", "thumb-1", 3, 7L,
                Long.MAX_VALUE);
        NodeAccessTokenService.ParsedToken parsed = service.parse(issued.token());
        assertEquals("node-1", parsed.nodeId());
        assertEquals("key-1", parsed.keyId());
        assertEquals("thumb-1", parsed.keyThumbprint());
        assertEquals(3, parsed.accessVersion());
        assertEquals(7L, parsed.sessionEpoch());
        assertTrue(parsed.expiresAt() > System.currentTimeMillis());
    }

    @Test
    void tokenTtlIsCappedByHardDeadline() {
        NodeAccessTokenService service = new NodeAccessTokenService(SECRET, AUDIENCE, 900);
        long deadline = System.currentTimeMillis() + 5000L;
        NodeAccessTokenService.IssuedToken issued = service.issue("node-1", "key-1", "thumb", 1, 1L, deadline);
        assertTrue(issued.expiresAt() <= deadline);
    }

    @Test
    void rejectsExpiredHardDeadline() {
        NodeAccessTokenService service = new NodeAccessTokenService(SECRET, AUDIENCE, 900);
        assertThrows(IllegalStateException.class,
                () -> service.issue("node-1", "key-1", "thumb", 1, 1L, System.currentTimeMillis() - 1));
    }

    @Test
    void rejectsWrongSigningSecret() {
        NodeAccessTokenService issuer = new NodeAccessTokenService(SECRET, AUDIENCE, 900);
        NodeAccessTokenService verifier = new NodeAccessTokenService(
                "ffffffffffffffffffffffffffffffff", AUDIENCE, 900);
        String token = issuer.issue("node-1", "key-1", "thumb", 1, 1L, Long.MAX_VALUE).token();
        assertThrows(IllegalArgumentException.class, () -> verifier.parse(token));
    }

    @Test
    void rejectsAudienceMismatch() {
        NodeAccessTokenService issuer = new NodeAccessTokenService(SECRET, AUDIENCE, 900);
        NodeAccessTokenService verifier = new NodeAccessTokenService(SECRET, "other.audience", 900);
        String token = issuer.issue("node-1", "key-1", "thumb", 1, 1L, Long.MAX_VALUE).token();
        assertThrows(IllegalArgumentException.class, () -> verifier.parse(token));
    }

    @Test
    void rejectsShortSecretAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new NodeAccessTokenService("short", AUDIENCE, 900));
    }

    @Test
    void rejectsUnsignedAlgNoneToken() {
        NodeAccessTokenService service = new NodeAccessTokenService(SECRET, AUDIENCE, 900);
        String payload = service.issue("node-1", "key-1", "thumb", 1, 1L, Long.MAX_VALUE)
                .token().split("\\.")[1];
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> service.parse(header + "." + payload + "."));
    }

    @Test
    void rejectsAlgorithmSubstitutionHeader() {
        NodeAccessTokenService service = new NodeAccessTokenService(SECRET, AUDIENCE, 900);
        String issued = service.issue("node-1", "key-1", "thumb", 1, 1L, Long.MAX_VALUE).token();
        String[] parts = issued.split("\\.");
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        // 保留合法 HS256 签名，但把头部 alg 改为 none：必须因算法不允许而拒绝。
        assertThrows(IllegalArgumentException.class,
                () -> service.parse(header + "." + parts[1] + "." + parts[2]));
    }

    @Test
    void rejectsTamperedPayload() {
        NodeAccessTokenService service = new NodeAccessTokenService(SECRET, AUDIENCE, 900);
        String issued = service.issue("node-1", "key-1", "thumb", 1, 1L, Long.MAX_VALUE).token();
        String[] parts = issued.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.replace("node-1", "node-2").getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,
                () -> service.parse(parts[0] + "." + tamperedPayload + "." + parts[2]));
    }

    @Test
    void rejectsNonPositiveSessionEpoch() {
        NodeAccessTokenService service = new NodeAccessTokenService(SECRET, AUDIENCE, 900);
        assertThrows(IllegalArgumentException.class, () -> service.parse(signedWithEpoch(0L)));
    }

    @Test
    void rejectsFractionalAndOutOfRangeNumericClaims() {
        NodeAccessTokenService service = new NodeAccessTokenService(SECRET, AUDIENCE, 900);
        assertThrows(IllegalArgumentException.class, () -> service.parse(signedWithEpoch(1.5d)));
        assertThrows(IllegalArgumentException.class, () -> service.parse(signedWithEpoch("3")));
    }

    @Test
    void rejectsNonIntegralBigDecimalAndOverflowingEpochSeconds() {
        NodeAccessTokenService service = new NodeAccessTokenService(SECRET, AUDIENCE, 900);
        // 精确小数：1.0000000000000000001 不能被 double 舍入成 1 而放行。
        assertThrows(IllegalArgumentException.class,
                () -> service.parse(signedWithEpoch(new java.math.BigDecimal("1.0000000000000000001"))));
        // 秒级 exp 远超 safe*1000 范围：乘法溢出后不得落到合法未来时间。
        long now = System.currentTimeMillis() / 1000L;
        assertThrows(IllegalArgumentException.class,
                () -> service.parse(signedWithOverriddenExpiry((1L << 61) + now + 60L)));
    }

    /**
     * 用与生产相同的密钥手工签发一个含指定 sessionEpoch 的令牌，用于校验严格数值解析。
     */
    private String signedWithEpoch(Object sessionEpoch) {
        long now = System.currentTimeMillis();
        JWT jwt = JWT.create()
                .setPayload(NodeAccessTokenService.CLAIM_TYPE, NodeProtocolConstants.TOKEN_TYPE_NODE_ACCESS)
                .setPayload(NodeAccessTokenService.CLAIM_AUDIENCE, AUDIENCE)
                .setPayload(NodeAccessTokenService.CLAIM_SUBJECT, "node-1")
                .setPayload(NodeAccessTokenService.CLAIM_KEY_ID, "key-1")
                .setPayload(NodeAccessTokenService.CLAIM_KEY_THUMBPRINT, "thumb-1")
                .setPayload(NodeAccessTokenService.CLAIM_ACCESS_VERSION, 1)
                .setPayload(NodeAccessTokenService.CLAIM_SESSION_EPOCH, sessionEpoch)
                .setPayload(NodeAccessTokenService.CLAIM_SCOPE, NodeProtocolConstants.TOKEN_SCOPE_NODE_TASK)
                .setPayload(NodeAccessTokenService.CLAIM_JWT_ID, "jti-1")
                .setIssuedAt(new Date(now))
                .setExpiresAt(new Date(now + 60_000L));
        return jwt.setKey(SECRET.getBytes(StandardCharsets.UTF_8)).sign();
    }

    /**
     * 手工签发一个 exp 被替换为指定秒级时间戳的令牌，用于校验乘法前的范围检查。
     */
    private String signedWithOverriddenExpiry(long expirySeconds) {
        long now = System.currentTimeMillis();
        JWT jwt = JWT.create()
                .setPayload(NodeAccessTokenService.CLAIM_TYPE, NodeProtocolConstants.TOKEN_TYPE_NODE_ACCESS)
                .setPayload(NodeAccessTokenService.CLAIM_AUDIENCE, AUDIENCE)
                .setPayload(NodeAccessTokenService.CLAIM_SUBJECT, "node-1")
                .setPayload(NodeAccessTokenService.CLAIM_KEY_ID, "key-1")
                .setPayload(NodeAccessTokenService.CLAIM_KEY_THUMBPRINT, "thumb-1")
                .setPayload(NodeAccessTokenService.CLAIM_ACCESS_VERSION, 1)
                .setPayload(NodeAccessTokenService.CLAIM_SESSION_EPOCH, 1L)
                .setPayload(NodeAccessTokenService.CLAIM_SCOPE, NodeProtocolConstants.TOKEN_SCOPE_NODE_TASK)
                .setPayload(NodeAccessTokenService.CLAIM_JWT_ID, "jti-1")
                .setIssuedAt(new Date(now))
                .setPayload("exp", expirySeconds);
        return jwt.setKey(SECRET.getBytes(StandardCharsets.UTF_8)).sign();
    }
}
