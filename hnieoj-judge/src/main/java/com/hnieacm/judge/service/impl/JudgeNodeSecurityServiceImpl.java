package com.hnieacm.judge.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.constant.JudgeNodeConstant;
import com.hnieacm.judge.dto.CreateJudgeAuthCodeRequest;
import com.hnieacm.judge.dto.ExchangeJudgeTempTokenRequest;
import com.hnieacm.judge.dto.ValidateJudgeNodeTokenRequest;
import com.hnieacm.judge.entity.JudgeNodeAuthCode;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeAuthCodeMapper;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.properties.JudgeSecurityProperties;
import com.hnieacm.judge.service.FormalJudgeTokenService;
import com.hnieacm.judge.service.JudgeNodeSecurityService;
import com.hnieacm.judge.vo.JudgeAuthCodeVo;
import com.hnieacm.judge.vo.JudgeNodeTokenValidationVo;
import com.hnieacm.judge.vo.JudgeNodeTokenVo;
import com.hnieacm.judge.vo.JudgeTempTokenVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点安全服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeNodeSecurityServiceImpl implements JudgeNodeSecurityService {

    private static final int AUTH_CODE_RANDOM_BYTES = 32;
    private static final String TOKEN_TYPE = "Bearer";
    private static final String CLAIM_TOKEN_ID = "tokenId";
    private static final String CLAIM_NODE_ID = "nodeId";
    private static final String CLAIM_NODE_TYPE = "type";
    private static final String CLAIM_NODE_NAME = "nodeName";
    private static final String CLAIM_FINGERPRINT_HASH = "fingerprintHash";
    private static final String CLAIM_BOUND_SOURCE_IP = "boundSourceIp";
    private static final String CLAIM_CNF = "cnf";
    private static final String CLAIM_EXPIRE_TIME = "exp";
    private static final String CLAIM_ISSUED_AT = "iat";
    private static final String DEFAULT_SECRET_MARK = "replace_me";
    private static final long HEARTBEAT_ONLINE_TIMEOUT_SECONDS = 90;
    private static final String PROOF_TYPE_ED25519 = "ed25519";
    private static final String NONCE_KEY_PREFIX = "hnieoj:judge:temp-token:nonce:";
    private static final byte[] ED25519_X509_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

    private final JudgeNodeAuthCodeMapper authCodeMapper;
    private final JudgeNodeTokenMapper tokenMapper;
    private final JudgeSecurityProperties securityProperties;
    private final FormalJudgeTokenService formalJudgeTokenService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @MethodName createAuthCode
     * @Param request
     * @Description 创建身份验证代码
     * @Return @return {@link JudgeAuthCodeVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeAuthCodeVo createAuthCode(CreateJudgeAuthCodeRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        long ttlSeconds = request.getExpireSeconds();
        int maxExchangeCount = request.getMaxExchangeCount();
        if (ttlSeconds <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "expireSeconds 必须大于 0");
        }
        if (maxExchangeCount <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "maxExchangeCount 必须大于 0");
        }

        String rawCode = generateAuthCode();
        JudgeNodeAuthCode authCode = new JudgeNodeAuthCode();
        authCode.setCodeHash(hash(rawCode));
        authCode.setNodeName(trimToNull(request == null ? null : request.getNodeName()));
        authCode.setCreatedBy(StpUtil.getLoginIdAsString());
        authCode.setRemark(trimToNull(request == null ? null : request.getRemark()));
        authCode.setMaxExchangeCount(maxExchangeCount);
        authCode.setUsedCount(0);
        authCode.setStatus(JudgeNodeConstant.AUTH_CODE_ENABLED);
        authCode.setExpireTime(LocalDateTime.now().plusSeconds(ttlSeconds));
        authCodeMapper.insert(authCode);

        JudgeAuthCodeVo vo = new JudgeAuthCodeVo();
        vo.setId(authCode.getId());
        vo.setAuthCode(rawCode);
        vo.setNodeName(authCode.getNodeName());
        vo.setMaxExchangeCount(authCode.getMaxExchangeCount());
        vo.setExpireTime(authCode.getExpireTime());
        return vo;
    }

    /**
     * @MethodName exchangeTempToken
     * @Param request
     * @Description 交换临时令牌
     * @Return @return {@link JudgeTempTokenVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeTempTokenVo exchangeTempToken(ExchangeJudgeTempTokenRequest request) {
        if (request == null || StrUtil.isBlank(request.getAuthCode())) {
            throw new BizException(ResultCode.BAD_REQUEST, "authCode 不能为空");
        }
        validateExchangeBindingRequest(request);

        String codeHash = hash(request.getAuthCode().trim());
        JudgeNodeAuthCode authCode = authCodeMapper.selectOne(new LambdaQueryWrapper<JudgeNodeAuthCode>()
                .eq(JudgeNodeAuthCode::getCodeHash, codeHash)
                .last("limit 1"));
        if (authCode == null || !JudgeNodeConstant.AUTH_CODE_ENABLED.equals(authCode.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "授权码无效");
        }
        LocalDateTime now = LocalDateTime.now();
        if (authCode.getExpireTime() == null || authCode.getExpireTime().isBefore(now)) {
            markAuthCodeExpired(authCode);
            throw new BizException(ResultCode.FORBIDDEN, "授权码已过期");
        }
        int usedCount = authCode.getUsedCount() == null ? 0 : authCode.getUsedCount();
        int maxExchangeCount = authCode.getMaxExchangeCount() == null ? 1 : authCode.getMaxExchangeCount();
        if (usedCount >= maxExchangeCount) {
            throw new BizException(ResultCode.FORBIDDEN, "授权码已达到最大兑换次数");
        }

        int occupied = authCodeMapper.update(null, new LambdaUpdateWrapper<JudgeNodeAuthCode>()
                .eq(JudgeNodeAuthCode::getId, authCode.getId())
                .eq(JudgeNodeAuthCode::getStatus, JudgeNodeConstant.AUTH_CODE_ENABLED)
                .gt(JudgeNodeAuthCode::getExpireTime, now)
                .lt(JudgeNodeAuthCode::getUsedCount, maxExchangeCount)
                .setSql("used_count = used_count + 1"));
        if (occupied <= 0) {
            throw new BizException(ResultCode.FORBIDDEN, "授权码已失效");
        }

        String nodeId = UUID.randomUUID().toString().replace("-", "");
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expireTime = now.plusSeconds(securityProperties.getTempTokenTtlSeconds());
        String nodeName = resolveNodeName(request, authCode);
        String fingerprintHash = calculateFingerprintHash(request.getFingerprint(), nodeName);
        String publicKey = trimToNull(request.getProof().getPublicKey());
        String sourceIp = currentRequestSourceIp();

        JudgeNodeToken tokenRecord = new JudgeNodeToken();
        tokenRecord.setTokenId(tokenId);
        tokenRecord.setNodeId(nodeId);
        tokenRecord.setNodeName(nodeName);
        tokenRecord.setNodeType(JudgeNodeConstant.NODE_TYPE_TEMP);
        tokenRecord.setStatus(JudgeNodeConstant.TOKEN_ACTIVE);
        tokenRecord.setAuthCodeId(authCode.getId());
        tokenRecord.setInstanceId(trimToNull(request.getFingerprint().getInstanceId()));
        tokenRecord.setFingerprintHash(fingerprintHash);
        tokenRecord.setBoundSourceIp(sourceIp);
        tokenRecord.setProofType(PROOF_TYPE_ED25519);
        tokenRecord.setPublicKey(publicKey);
        tokenRecord.setPublicKeyHash(hash(publicKey));
        tokenRecord.setSupportedJudgeModes(toCsv(request.getFingerprint().getSupportedJudgeModes()));
        tokenRecord.setExpireTime(expireTime);
        tokenMapper.insert(tokenRecord);

        String jwt = createJwt(tokenRecord, expireTime);
        JudgeTempTokenVo vo = new JudgeTempTokenVo();
        vo.setToken(jwt);
        vo.setTokenType(TOKEN_TYPE);
        vo.setNodeId(nodeId);
        vo.setTokenId(tokenId);
        vo.setFingerprintHash(fingerprintHash);
        vo.setExpireTime(expireTime);
        return vo;
    }

    /**
     * @MethodName listTokens
     * @Param status
     * @Description 列出令牌
     * @Return @return {@link List }<{@link JudgeNodeTokenVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    @Override
    public List<JudgeNodeTokenVo> listTokens(String status) {
        LambdaQueryWrapper<JudgeNodeToken> wrapper = new LambdaQueryWrapper<JudgeNodeToken>()
                .orderByDesc(JudgeNodeToken::getGmtCreate)
                .orderByDesc(JudgeNodeToken::getId);
        String normalizedStatus = trimToNull(status);
        if (normalizedStatus != null) {
            wrapper.eq(JudgeNodeToken::getStatus, normalizedStatus);
        }
        return tokenMapper.selectList(wrapper).stream().map(this::toTokenVo).toList();
    }

    /**
     * @MethodName revokeToken
     * @Param tokenId
     * @Description 撤销令牌
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeToken(String tokenId) {
        String normalizedTokenId = trimToNull(tokenId);
        if (normalizedTokenId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "tokenId 不能为空");
        }
        int updated = tokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, normalizedTokenId)
                .set(JudgeNodeToken::getStatus, JudgeNodeConstant.TOKEN_REVOKED)
                .set(JudgeNodeToken::getRevokedBy, StpUtil.getLoginIdAsString())
                .set(JudgeNodeToken::getRevokedTime, LocalDateTime.now()));
        if (updated <= 0) {
            throw new BizException(ResultCode.NOT_FOUND, "Token 不存在");
        }
    }

    /**
     * @MethodName validateToken
     * @Param request
     * @Description 验证令牌
     * @Return @return {@link JudgeNodeTokenValidationVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeNodeTokenValidationVo validateToken(ValidateJudgeNodeTokenRequest request) {
        String judgeToken = trimToNull(request == null ? null : request.getJudgeToken());
        if (judgeToken != null && validateFormalToken(judgeToken)) {
            return validationResult(true, JudgeNodeConstant.NODE_TYPE_FORMAL, "formal", null);
        }

        String bearerToken = trimToNull(request == null ? null : request.getBearerToken());
        if (bearerToken == null) {
            return validationResult(false, null, null, null);
        }
        return validateTempJwt(bearerToken, request);
    }

    /**
     * @MethodName validateFormalToken
     * @Param judgeToken
     * @Description 验证正式令牌
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private boolean validateFormalToken(String judgeToken) {
        return formalJudgeTokenService.matches(judgeToken);
    }

    private void validateExchangeBindingRequest(ExchangeJudgeTempTokenRequest request) {
        if (request.getFingerprint() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "fingerprint 不能为空");
        }
        if (StrUtil.isBlank(request.getFingerprint().getInstanceId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "fingerprint.instanceId 不能为空");
        }
        if (StrUtil.isBlank(request.getFingerprint().getHostnameHash())) {
            throw new BizException(ResultCode.BAD_REQUEST, "fingerprint.hostnameHash 不能为空");
        }
        if (StrUtil.isBlank(request.getFingerprint().getMachineIdHash())) {
            throw new BizException(ResultCode.BAD_REQUEST, "fingerprint.machineIdHash 不能为空");
        }
        if (request.getProof() == null || !PROOF_TYPE_ED25519.equalsIgnoreCase(trimToNull(request.getProof().getType()))) {
            throw new BizException(ResultCode.BAD_REQUEST, "proof.type 仅支持 ed25519");
        }
        if (StrUtil.isBlank(request.getProof().getPublicKey())) {
            throw new BizException(ResultCode.BAD_REQUEST, "proof.publicKey 不能为空");
        }
        parseEd25519PublicKey(request.getProof().getPublicKey());
    }

    private String calculateFingerprintHash(ExchangeJudgeTempTokenRequest.Fingerprint fingerprint, String nodeName) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("instanceId", trimToNull(fingerprint.getInstanceId()));
        normalized.put("nodeName", trimToNull(nodeName));
        normalized.put("hostnameHash", trimToNull(fingerprint.getHostnameHash()));
        normalized.put("machineIdHash", trimToNull(fingerprint.getMachineIdHash()));
        normalized.put("macAddressHashes", sortedTrimmed(fingerprint.getMacAddressHashes()));
        normalized.put("ipAddressHashes", sortedTrimmed(fingerprint.getIpAddressHashes()));
        normalized.put("supportedJudgeModes", normalizeJudgeModes(fingerprint.getSupportedJudgeModes()));
        normalized.put("clientTime", trimToNull(fingerprint.getClientTime()));
        try {
            return hash(objectMapper.writeValueAsString(normalized));
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "fingerprint 格式不合法");
        }
    }

    private boolean validateBoundRequest(ValidateJudgeNodeTokenRequest request, JudgeNodeToken tokenRecord,
                                         JWT jwt, String nodeId, String tokenId) {
        if (request == null) {
            return false;
        }
        String fingerprintHash = payloadToString(jwt.getPayload(CLAIM_FINGERPRINT_HASH));
        String boundSourceIp = payloadToString(jwt.getPayload(CLAIM_BOUND_SOURCE_IP));
        String publicKeyHash = cnfPublicKeyHash(jwt.getPayload(CLAIM_CNF));
        if (!Objects.equals(tokenRecord.getFingerprintHash(), fingerprintHash)
                || !Objects.equals(tokenRecord.getBoundSourceIp(), boundSourceIp)
                || !Objects.equals(tokenRecord.getPublicKeyHash(), publicKeyHash)) {
            return false;
        }
        if (!Objects.equals(nodeId, trimToNull(request.getNodeIdHeader()))
                || !Objects.equals(tokenId, trimToNull(request.getTokenIdHeader()))
                || !Objects.equals(tokenRecord.getInstanceId(), trimToNull(request.getInstanceId()))
                || !Objects.equals(tokenRecord.getFingerprintHash(), trimToNull(request.getFingerprintHash()))) {
            return false;
        }
        if (!Objects.equals(tokenRecord.getBoundSourceIp(), trimToNull(request.getSourceIp()))) {
            return false;
        }
        if (!PROOF_TYPE_ED25519.equalsIgnoreCase(trimToNull(request.getSignatureAlgorithm()))) {
            return false;
        }
        if (!validateBodyHash(request) || !validateTimestamp(request.getTimestamp())
                || !consumeNonce(tokenId, request.getNonce())) {
            return false;
        }
        return verifyEd25519Signature(tokenRecord.getPublicKey(), request);
    }

    private boolean validateBodyHash(ValidateJudgeNodeTokenRequest request) {
        String bodySha256 = trimToNull(request.getBodySha256());
        String actualBodySha256 = trimToNull(request.getActualBodySha256());
        return bodySha256 != null && bodySha256.equalsIgnoreCase(actualBodySha256);
    }

    private String cnfPublicKeyHash(Object cnf) {
        if (!(cnf instanceof Map<?, ?> cnfMap)) {
            return null;
        }
        Object type = cnfMap.get("type");
        Object publicKeyHash = cnfMap.get("publicKeyHash");
        if (!PROOF_TYPE_ED25519.equalsIgnoreCase(payloadToString(type))) {
            return null;
        }
        return payloadToString(publicKeyHash);
    }

    private boolean validateTimestamp(String rawTimestamp) {
        Long timestamp = parseLong(rawTimestamp);
        if (timestamp == null) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000L;
        long skew = Math.max(1L, securityProperties.getTempTokenAllowedClockSkewSeconds());
        return Math.abs(now - timestamp) <= skew;
    }

    private boolean consumeNonce(String tokenId, String nonce) {
        String normalizedNonce = trimToNull(nonce);
        if (normalizedNonce == null) {
            return false;
        }
        String key = NONCE_KEY_PREFIX + tokenId + ":" + normalizedNonce;
        long ttl = Math.max(1L, securityProperties.getTempTokenNonceTtlSeconds());
        try {
            Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", ttl, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.warn("Judge node nonce cache failed, tokenId: {}", tokenId, e);
            return false;
        }
    }

    private boolean verifyEd25519Signature(String publicKey, ValidateJudgeNodeTokenRequest request) {
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(parseEd25519PublicKey(publicKey));
            verifier.update(signingString(request).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(trimToNull(request.getSignature())));
        } catch (Exception e) {
            log.warn("Judge node signature verify failed, tokenId: {}", request.getTokenIdHeader(), e);
            return false;
        }
    }

    private String signingString(ValidateJudgeNodeTokenRequest request) {
        String method = trimToNull(request.getMethod());
        String pathWithQuery = trimToNull(request.getPathWithQuery());
        String bodySha256 = trimToNull(request.getBodySha256());
        String timestamp = trimToNull(request.getTimestamp());
        String nonce = trimToNull(request.getNonce());
        if (method == null || pathWithQuery == null || bodySha256 == null || timestamp == null || nonce == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "判题节点签名头不完整");
        }
        return method.toUpperCase() + "\n"
                + pathWithQuery + "\n"
                + bodySha256 + "\n"
                + timestamp + "\n"
                + nonce;
    }

    private java.security.PublicKey parseEd25519PublicKey(String publicKey) {
        try {
            byte[] bytes = Base64.getDecoder().decode(trimToNull(publicKey));
            if (bytes.length == 32) {
                byte[] x509Bytes = new byte[ED25519_X509_PREFIX.length + bytes.length];
                System.arraycopy(ED25519_X509_PREFIX, 0, x509Bytes, 0, ED25519_X509_PREFIX.length);
                System.arraycopy(bytes, 0, x509Bytes, ED25519_X509_PREFIX.length, bytes.length);
                bytes = x509Bytes;
            }
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "proof.publicKey 格式不合法");
        }
    }

    /**
     * @MethodName validateTempJwt
     * @Param token
     * @Description 验证临时jwt
     * @Return @return {@link JudgeNodeTokenValidationVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private JudgeNodeTokenValidationVo validateTempJwt(String token, ValidateJudgeNodeTokenRequest request) {
        JWT jwt;
        try {
            if (!JWTUtil.verify(token, jwtKey())) {
                return validationResult(false, null, null, null);
            }
            jwt = JWTUtil.parseToken(token).setKey(jwtKey());
        } catch (Exception e) {
            log.warn("Validate judge temp jwt failed: {}", e.getMessage());
            return validationResult(false, null, null, null);
        }
        String tokenId = payloadToString(jwt.getPayload(CLAIM_TOKEN_ID));
        String nodeId = payloadToString(jwt.getPayload(CLAIM_NODE_ID));
        String nodeType = payloadToString(jwt.getPayload(CLAIM_NODE_TYPE));
        Long expireMillis = parseLong(jwt.getPayload(CLAIM_EXPIRE_TIME));
        if (!JudgeNodeConstant.NODE_TYPE_TEMP.equals(nodeType) || StrUtil.isBlank(tokenId)
                || StrUtil.isBlank(nodeId) || expireMillis == null || expireMillis < System.currentTimeMillis()) {
            return validationResult(false, null, null, null);
        }

        JudgeNodeToken tokenRecord = tokenMapper.selectOne(new LambdaQueryWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, tokenId)
                .last("limit 1"));
        if (tokenRecord == null || !JudgeNodeConstant.TOKEN_ACTIVE.equals(tokenRecord.getStatus())
                || tokenRecord.getExpireTime() == null || tokenRecord.getExpireTime().isBefore(LocalDateTime.now())) {
            markTokenExpiredIfNeeded(tokenRecord);
            return validationResult(false, null, null, null);
        }
        if (!validateBoundRequest(request, tokenRecord, jwt, nodeId, tokenId)) {
            return validationResult(false, null, null, null);
        }

        tokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, tokenId)
                .set(JudgeNodeToken::getLastUsedTime, LocalDateTime.now())
                .set(JudgeNodeToken::getLastSeenAt, LocalDateTime.now())
                .set(JudgeNodeToken::getLastSeenIp, trimToNull(request.getSourceIp())));
        return validationResult(true, nodeType, nodeId, tokenId);
    }

    /**
     * @MethodName createJwt
     * @Param tokenRecord
     * @Param expireTime
     * @Description 创建jwt
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private String createJwt(JudgeNodeToken tokenRecord, LocalDateTime expireTime) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(CLAIM_TOKEN_ID, tokenRecord.getTokenId());
        payload.put(CLAIM_NODE_ID, tokenRecord.getNodeId());
        payload.put(CLAIM_NODE_NAME, tokenRecord.getNodeName());
        payload.put(CLAIM_NODE_TYPE, tokenRecord.getNodeType());
        payload.put(CLAIM_FINGERPRINT_HASH, tokenRecord.getFingerprintHash());
        payload.put(CLAIM_BOUND_SOURCE_IP, tokenRecord.getBoundSourceIp());
        payload.put("supportedJudgeModes", splitSupportedJudgeModes(tokenRecord.getSupportedJudgeModes()));
        payload.put(CLAIM_CNF, Map.of(
                "type", tokenRecord.getProofType(),
                "publicKeyHash", tokenRecord.getPublicKeyHash()
        ));
        payload.put(CLAIM_ISSUED_AT, System.currentTimeMillis());
        payload.put(CLAIM_EXPIRE_TIME, expireTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return JWTUtil.createToken(payload, jwtKey());
    }

    /**
     * @MethodName jwtKey
     *
     * @Description jwt密钥
     * @Return @return {@link byte[] }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private byte[] jwtKey() {
        if (StrUtil.isBlank(securityProperties.getJwtSecret())
                || securityProperties.getJwtSecret().contains(DEFAULT_SECRET_MARK)) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "判题节点 JWT 密钥未正确配置");
        }
        return securityProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @MethodName generateAuthCode
     *
     * @Description 生成认证码
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private String generateAuthCode() {
        byte[] bytes = new byte[AUTH_CODE_RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * @MethodName hash
     * @Param value
     * @Description 哈希
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private String hash(String value) {
        return SecureUtil.sha256(value);
    }

    /**
     * @MethodName markAuthCodeExpired
     * @Param authCode
     * @Description 标记身份验证码已过期
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private void markAuthCodeExpired(JudgeNodeAuthCode authCode) {
        authCodeMapper.update(null, new LambdaUpdateWrapper<JudgeNodeAuthCode>()
                .eq(JudgeNodeAuthCode::getId, authCode.getId())
                .set(JudgeNodeAuthCode::getStatus, JudgeNodeConstant.AUTH_CODE_EXPIRED));
    }

    /**
     * @MethodName markTokenExpiredIfNeeded
     * @Param tokenRecord
     * @Description 手动标记令牌已过期
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private void markTokenExpiredIfNeeded(JudgeNodeToken tokenRecord) {
        if (tokenRecord != null && JudgeNodeConstant.TOKEN_ACTIVE.equals(tokenRecord.getStatus())
                && tokenRecord.getExpireTime() != null && tokenRecord.getExpireTime().isBefore(LocalDateTime.now())) {
            tokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                    .eq(JudgeNodeToken::getId, tokenRecord.getId())
                    .set(JudgeNodeToken::getStatus, JudgeNodeConstant.TOKEN_EXPIRED));
        }
    }

    /**
     * @MethodName resolveNodeName
     * @Param request
     * @Param authCode
     * @Description 解析节点名称
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private String resolveNodeName(ExchangeJudgeTempTokenRequest request, JudgeNodeAuthCode authCode) {
        String nodeName = trimToNull(request.getNodeName());
        if (nodeName != null) {
            return nodeName;
        }
        return authCode.getNodeName();
    }

    /**
     * @MethodName validationResult
     * @Param valid
     * @Param nodeType
     * @Param nodeId
     * @Param tokenId
     * @Description 验证结果
     * @Return @return {@link JudgeNodeTokenValidationVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private JudgeNodeTokenValidationVo validationResult(boolean valid, String nodeType, String nodeId, String tokenId) {
        JudgeNodeTokenValidationVo vo = new JudgeNodeTokenValidationVo();
        vo.setValid(valid);
        vo.setNodeType(nodeType);
        vo.setNodeId(nodeId);
        vo.setTokenId(tokenId);
        return vo;
    }

    /**
     * @MethodName toTokenVo
     * @Param token
     * @Description 标记vo
     * @Return @return {@link JudgeNodeTokenVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private JudgeNodeTokenVo toTokenVo(JudgeNodeToken token) {
        JudgeNodeTokenVo vo = new JudgeNodeTokenVo();
        vo.setId(token.getId());
        vo.setTokenId(token.getTokenId());
        vo.setNodeId(token.getNodeId());
        vo.setNodeName(token.getNodeName());
        vo.setNodeType(token.getNodeType());
        vo.setStatus(token.getStatus());
        vo.setExpireTime(token.getExpireTime());
        vo.setLastUsedTime(token.getLastUsedTime());
        vo.setLastHeartbeatTime(token.getLastHeartbeatTime());
        vo.setOnline(isOnline(token));
        vo.setMaxConcurrency(token.getMaxConcurrency());
        vo.setRunningTasks(token.getRunningTasks());
        vo.setCpuCore(token.getCpuCore());
        vo.setVersion(token.getVersion());
        vo.setSupportedJudgeModes(splitSupportedJudgeModes(token.getSupportedJudgeModes()));
        vo.setCacheUsedBytes(token.getCacheUsedBytes());
        vo.setCacheProblemCount(token.getCacheProblemCount());
        vo.setDiskTotalBytes(token.getDiskTotalBytes());
        vo.setDiskFreeBytes(token.getDiskFreeBytes());
        vo.setGmtCreate(token.getGmtCreate());
        return vo;
    }

    private Boolean isOnline(JudgeNodeToken token) {
        if (token.getLastHeartbeatTime() == null || !JudgeNodeConstant.TOKEN_ACTIVE.equals(token.getStatus())) {
            return false;
        }
        return token.getLastHeartbeatTime().isAfter(LocalDateTime.now().minusSeconds(HEARTBEAT_ONLINE_TIMEOUT_SECONDS));
    }

    private List<String> splitSupportedJudgeModes(String supportedJudgeModes) {
        String normalizedModes = StrUtil.blankToDefault(supportedJudgeModes, "default");
        return Arrays.stream(normalizedModes.split(","))
                .map(StrUtil::trimToNull)
                .filter(item -> item != null)
                .toList();
    }

    private List<String> normalizeJudgeModes(List<String> modes) {
        List<String> normalized = sortedTrimmed(modes);
        return normalized.isEmpty() ? List.of("default") : normalized;
    }

    private List<String> sortedTrimmed(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream()
                .map(StrUtil::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String toCsv(List<String> values) {
        return String.join(",", normalizeJudgeModes(values));
    }

    private String currentRequestSourceIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "无法获取请求来源 IP");
        }
        HttpServletRequest request = attributes.getRequest();
        String forwardedFor = trimToNull(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = trimToNull(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * @MethodName parseLong
     * @Param value
     * @Description 转长整型
     * @Return @return {@link Long }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @MethodName payloadToString
     * @Param value
     * @Description 有效载荷到字符串
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private String payloadToString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    /**
     * @MethodName trimToNull
     * @Param value
     * @Description 修剪为零
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private String trimToNull(String value) {
        return StrUtil.trimToNull(value);
    }
}
