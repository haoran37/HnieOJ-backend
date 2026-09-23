package com.hnieacm.judge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.judge.NodeAccessTokenService;
import com.hnieacm.common.judge.NodeProtocolConstants;
import com.hnieacm.common.judge.NodeSignatureCodec;
import com.hnieacm.judge.dto.NodeAuthChallenge;
import com.hnieacm.judge.dto.NodeAuthResult;
import com.hnieacm.judge.entity.JudgeNodeKey;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeAuthCodeMapper;
import com.hnieacm.judge.mapper.JudgeNodeKeyMapper;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.properties.JudgeSecurityProperties;
import com.hnieacm.judge.properties.NodeSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 节点身份服务的会话纪元/刷新绑定/会话所有权回归测试。
 *
 * <p>覆盖 Codex 复现的安全缺陷：并发接管纪元、旧连接刷新伪装当前会话、
 * accessVersion 失效、吊销/禁用/密钥过期后 WSS 业务仍被放行。</p>
 *
 * @author Codex
 */
@ExtendWith(MockitoExtension.class)
class NodeIdentityServiceImplTest {

    @Test
    void heartbeatUsesApplicationTimeAndRejectsStaleSession() {
        LocalDateTime before = LocalDateTime.now();
        when(tokenMapper.touchSessionHeartbeat(eq(NODE_ID), eq(7L), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    LocalDateTime now = invocation.getArgument(2);
                    assertThat(now).isBetween(before, LocalDateTime.now());
                    return 1;
                });
        service.touchHeartbeat(NODE_ID, 7L);
        assertThatThrownBy(() -> service.touchHeartbeat(NODE_ID, 6L))
                .isInstanceOf(BizException.class).hasMessageContaining("会话已被接管");
    }

    private static final String NODE_ID = "node-1";
    private static final String KEY_ID = "key-1";
    private static final String AUDIENCE = "hnieoj-judge-node";
    private static final ZoneOffset ZONE = ZoneOffset.ofHours(8);

    @Mock
    private JudgeNodeAuthCodeMapper authCodeMapper;

    @Mock
    private JudgeNodeTokenMapper tokenMapper;

    @Mock
    private JudgeNodeKeyMapper keyMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NodeSecurityProperties nodeSecurityProperties;
    private NodeIdentityServiceImpl service;

    @BeforeEach
    void setUp() {
        nodeSecurityProperties = new NodeSecurityProperties();
        nodeSecurityProperties.setAccessTokenSecret("0123456789abcdef0123456789abcdef");
        JudgeSecurityProperties judgeSecurityProperties = new JudgeSecurityProperties();
        NodeAccessTokenService tokenService = new NodeAccessTokenService(
                nodeSecurityProperties.getAccessTokenSecret(), AUDIENCE, 900);
        service = new NodeIdentityServiceImpl(authCodeMapper, tokenMapper, keyMapper,
                nodeSecurityProperties, judgeSecurityProperties, tokenService,
                stringRedisTemplate, objectMapper);
    }

    @Test
    void initialAuthAllocatesDistinctEpochUnderRowLock() {
        // 行锁读到 5，自增后本次连接必须拿到 6，而不是其他并发连接的纪元。
        JudgeNodeKey key = activeKey();
        JudgeNodeToken node = activeNode();
        when(tokenMapper.lockNode(NODE_ID)).thenReturn(node);
        when(tokenMapper.incrementSessionEpoch(NODE_ID)).thenReturn(1);
        when(keyMapper.selectOne(any())).thenReturn(key);
        String nonce = "bm9uY2U=";
        String signature = sign(key, NodeProtocolConstants.DOMAIN_AUTH, AUDIENCE,
                "challenge-1", nonce, NODE_ID, KEY_ID);
        stubChallenge(false, null, null, nonce);

        NodeAuthResult result = service.authenticate("conn-1", NODE_ID, KEY_ID, "challenge-1", REQUEST_ID, signature);
        assertThat(result.getSessionEpoch()).isEqualTo(6L);
        assertThat(result.getAccessVersion()).isEqualTo(1);
        // 认证结果携带权威状态，供连接认证成功后通过 NODE_STATE 暴露给 Agent。
        assertThat(result.getStatus()).isEqualTo(NodeProtocolConstants.NODE_STATUS_ACTIVE);
        assertThat(result.getDraining()).isFalse();
    }

    @Test
    void refreshWithStaleEpochBindingIsRejected() {
        JudgeNodeKey key = activeKey();
        JudgeNodeToken node = activeNode();
        // 刷新挑战绑定创建时的旧纪元 5，但节点已被接管为 7：节点行锁内读到最新纪元。
        node.setSessionEpoch(7L);
        when(tokenMapper.lockNode(NODE_ID)).thenReturn(node);
        when(keyMapper.selectOne(any())).thenReturn(key);
        String nonce = "bm9uY2U=";
        String signature = sign(key, NodeProtocolConstants.DOMAIN_AUTH, AUDIENCE,
                "challenge-2", nonce, NODE_ID, KEY_ID);
        stubChallenge(true, NODE_ID, 5L, nonce);

        assertThatThrownBy(() -> service.authenticate("conn-1", NODE_ID, KEY_ID, "challenge-2", REQUEST_ID, signature))
                .isInstanceOf(BizException.class);
    }

    @Test
    void refreshBountToOtherNodeIsRejected() {
        stubChallenge(true, "other-node", 5L, "bm9uY2U=");
        assertThatThrownBy(() -> service.authenticate("conn-1", NODE_ID, KEY_ID, "challenge-3", REQUEST_ID, "sig"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void sessionOwnerRejectsAccessVersionMismatch() {
        JudgeNodeToken node = activeNode();
        node.setAccessVersion(2);
        when(tokenMapper.selectOne(any())).thenReturn(node);
        when(keyMapper.selectOne(any())).thenReturn(activeKey());

        assertThatThrownBy(() -> service.requireSessionOwner(NODE_ID, KEY_ID, 1L, 1, true))
                .isInstanceOf(BizException.class);
    }

    @Test
    void sessionOwnerRejectsRevokedNode() {
        JudgeNodeToken node = activeNode();
        node.setStatus(NodeProtocolConstants.NODE_STATUS_REVOKED);
        when(tokenMapper.selectOne(any())).thenReturn(node);

        assertThatThrownBy(() -> service.requireSessionOwner(NODE_ID, KEY_ID, 1L, 1, true))
                .isInstanceOf(BizException.class);
    }

    @Test
    void sessionOwnerRejectsExpiredGraceKey() {
        JudgeNodeToken node = activeNode();
        when(tokenMapper.selectOne(any())).thenReturn(node);
        JudgeNodeKey key = activeKey();
        key.setStatus(NodeProtocolConstants.KEY_STATUS_GRACE);
        key.setExpiresAt(LocalDateTime.ofInstant(Instant.now().minusSeconds(1), ZONE));
        when(keyMapper.selectOne(any())).thenReturn(key);

        assertThatThrownBy(() -> service.requireSessionOwner(NODE_ID, KEY_ID, 1L, 1, true))
                .isInstanceOf(BizException.class);
    }

    private static final String REQUEST_ID = "req-1";

    private void stubChallenge(boolean refresh, String bindNodeId, Long bindEpoch, String nonce) {
        NodeAuthChallenge challenge = new NodeAuthChallenge();
        challenge.setChallengeId("challenge-1");
        challenge.setRequestId(REQUEST_ID);
        challenge.setConnectionId("conn-1");
        challenge.setNonce(nonce);
        challenge.setAudience(AUDIENCE);
        challenge.setExpiresAt(System.currentTimeMillis() + 30_000L);
        challenge.setRefresh(refresh);
        challenge.setNodeId(bindNodeId);
        challenge.setSessionEpoch(bindEpoch);
        try {
            String json = objectMapper.writeValueAsString(challenge);
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.getAndDelete(anyString())).thenReturn(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JudgeNodeToken activeNode() {
        JudgeNodeToken node = new JudgeNodeToken();
        node.setTokenId(NODE_ID);
        node.setNodeId(NODE_ID);
        node.setStatus(NodeProtocolConstants.NODE_STATUS_ACTIVE);
        node.setSessionEpoch(5L);
        node.setAccessVersion(1);
        node.setActiveKeyId(KEY_ID);
        node.setMaxConcurrency(1);
        node.setSupportedJudgeModes("default");
        node.setExpireTime(LocalDateTime.now().plusDays(1));
        return node;
    }

    private JudgeNodeKey activeKey() {
        JudgeNodeKey key = new JudgeNodeKey();
        key.setKeyId(KEY_ID);
        key.setNodeId(NODE_ID);
        key.setStatus(NodeProtocolConstants.KEY_STATUS_ACTIVE);
        key.setPublicKey(PAIR.getPublicRawBase64());
        key.setPublicKeyHash(NodeSignatureCodec.sha256Hex(PAIR.getPublicRawBase64()));
        return key;
    }

    private static String sign(JudgeNodeKey key, String... fields) {
        // key 中持有公钥；签名使用生成对中的私钥，测试内自行保管。
        return PAIR.sign(NodeSignatureCodec.canonical(fields));
    }

    /** 测试用 Ed25519 公私钥对（私钥仅存在于本测试进程）。 */
    private static final TestEd25519KeyPair PAIR = TestEd25519KeyPair.generate();

    /**
     * 测试内 Ed25519 密钥对：裸 32 字节公钥 Base64 与签名辅助。
     */
    private static final class TestEd25519KeyPair {
        private final KeyPair keyPair;

        private TestEd25519KeyPair(KeyPair keyPair) {
            this.keyPair = keyPair;
        }

        private static TestEd25519KeyPair generate() {
            try {
                return new TestEd25519KeyPair(KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private String getPublicRawBase64() {
            byte[] x509 = keyPair.getPublic().getEncoded();
            byte[] raw = new byte[32];
            System.arraycopy(x509, x509.length - 32, raw, 0, 32);
            return Base64.getEncoder().encodeToString(raw);
        }

        private String sign(byte[] data) {
            try {
                Signature signature = Signature.getInstance("Ed25519");
                signature.initSign(keyPair.getPrivate());
                signature.update(data);
                return Base64.getEncoder().encodeToString(signature.sign());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
