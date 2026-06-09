package com.hnieacm.judge.service.impl;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.judge.constant.JudgeNodeConstant;
import com.hnieacm.judge.dto.ValidateJudgeNodeTokenRequest;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeAuthCodeMapper;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.properties.JudgeSecurityProperties;
import com.hnieacm.judge.service.FormalJudgeTokenService;
import com.hnieacm.judge.vo.JudgeNodeTokenValidationVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 判题节点安全服务测试
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class JudgeNodeSecurityServiceImplTest {

    private static final String JWT_SECRET = "test_judge_jwt_secret_12345678901234567890";
    private static final String NODE_ID = "node-1";
    private static final String TOKEN_ID = "token-1";
    private static final String INSTANCE_ID = "instance-1";
    private static final String FINGERPRINT_HASH = "fingerprint-hash";
    private static final String SOURCE_IP = "127.0.0.1";
    private static final String BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb924"
            + "27ae41e4649b934ca495991b7852b855";

    @Mock
    private JudgeNodeAuthCodeMapper authCodeMapper;

    @Mock
    private JudgeNodeTokenMapper tokenMapper;

    @Mock
    private FormalJudgeTokenService formalJudgeTokenService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private JudgeNodeSecurityServiceImpl service;
    private KeyPair keyPair;
    private String publicKey;

    @BeforeEach
    void setUp() throws Exception {
        JudgeSecurityProperties properties = new JudgeSecurityProperties();
        properties.setJwtSecret(JWT_SECRET);
        properties.setTempTokenAllowedClockSkewSeconds(300);
        properties.setTempTokenNonceTtlSeconds(600);
        service = new JudgeNodeSecurityServiceImpl(authCodeMapper, tokenMapper, properties,
                formalJudgeTokenService, new ObjectMapper(), stringRedisTemplate);
        initTableInfo(JudgeNodeToken.class);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        keyPair = generator.generateKeyPair();
        publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    @Test
    void shouldValidateSignedTempTokenRequest() throws Exception {
        when(tokenMapper.selectOne(any(Wrapper.class))).thenReturn(activeToken());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);

        JudgeNodeTokenValidationVo result = service.validateToken(validRequest("nonce-1", BODY_SHA256));

        assertThat(result.getValid()).isTrue();
        assertThat(result.getNodeType()).isEqualTo(JudgeNodeConstant.NODE_TYPE_TEMP);
        assertThat(result.getNodeId()).isEqualTo(NODE_ID);
        assertThat(result.getTokenId()).isEqualTo(TOKEN_ID);
    }

    @Test
    void shouldRejectReplayedNonce() throws Exception {
        when(tokenMapper.selectOne(any(Wrapper.class))).thenReturn(activeToken());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);

        JudgeNodeTokenValidationVo result = service.validateToken(validRequest("nonce-2", BODY_SHA256));

        assertThat(result.getValid()).isFalse();
    }

    @Test
    void shouldRejectBodyHashMismatch() throws Exception {
        when(tokenMapper.selectOne(any(Wrapper.class))).thenReturn(activeToken());

        JudgeNodeTokenValidationVo result = service.validateToken(validRequest("nonce-3", "bad-body-hash"));

        assertThat(result.getValid()).isFalse();
    }

    private ValidateJudgeNodeTokenRequest validRequest(String nonce, String actualBodySha256) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000L;
        ValidateJudgeNodeTokenRequest request = new ValidateJudgeNodeTokenRequest();
        request.setBearerToken(jwt());
        request.setMethod("POST");
        request.setPathWithQuery("/judge/nodes/heartbeat");
        request.setSourceIp(SOURCE_IP);
        request.setNodeIdHeader(NODE_ID);
        request.setTokenIdHeader(TOKEN_ID);
        request.setInstanceId(INSTANCE_ID);
        request.setFingerprintHash(FINGERPRINT_HASH);
        request.setSignatureAlgorithm("ed25519");
        request.setTimestamp(String.valueOf(timestamp));
        request.setNonce(nonce);
        request.setBodySha256(BODY_SHA256);
        request.setActualBodySha256(actualBodySha256);
        request.setSignature(sign(request));
        return request;
    }

    private String sign(ValidateJudgeNodeTokenRequest request) throws Exception {
        String signingString = request.getMethod() + "\n"
                + request.getPathWithQuery() + "\n"
                + request.getBodySha256() + "\n"
                + request.getTimestamp() + "\n"
                + request.getNonce();
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(keyPair.getPrivate());
        signature.update(signingString.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private String jwt() {
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(10);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tokenId", TOKEN_ID);
        payload.put("nodeId", NODE_ID);
        payload.put("nodeName", "node-name");
        payload.put("type", JudgeNodeConstant.NODE_TYPE_TEMP);
        payload.put("fingerprintHash", FINGERPRINT_HASH);
        payload.put("boundSourceIp", SOURCE_IP);
        payload.put("supportedJudgeModes", java.util.List.of("default"));
        payload.put("cnf", Map.of("type", "ed25519", "publicKeyHash", SecureUtil.sha256(publicKey)));
        payload.put("iat", System.currentTimeMillis());
        payload.put("exp", expireTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return JWTUtil.createToken(payload, JWT_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private JudgeNodeToken activeToken() {
        JudgeNodeToken token = new JudgeNodeToken();
        token.setTokenId(TOKEN_ID);
        token.setNodeId(NODE_ID);
        token.setNodeName("node-name");
        token.setNodeType(JudgeNodeConstant.NODE_TYPE_TEMP);
        token.setStatus(JudgeNodeConstant.TOKEN_ACTIVE);
        token.setInstanceId(INSTANCE_ID);
        token.setFingerprintHash(FINGERPRINT_HASH);
        token.setBoundSourceIp(SOURCE_IP);
        token.setProofType("ed25519");
        token.setPublicKey(publicKey);
        token.setPublicKeyHash(SecureUtil.sha256(publicKey));
        token.setExpireTime(LocalDateTime.now().plusMinutes(10));
        return token;
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
