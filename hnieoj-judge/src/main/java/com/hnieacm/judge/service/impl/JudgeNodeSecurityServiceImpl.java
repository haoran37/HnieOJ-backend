package com.hnieacm.judge.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private static final String CLAIM_EXPIRE_TIME = "exp";
    private static final String CLAIM_ISSUED_AT = "iat";
    private static final String DEFAULT_SECRET_MARK = "replace_me";
    private static final long HEARTBEAT_ONLINE_TIMEOUT_SECONDS = 90;

    private final JudgeNodeAuthCodeMapper authCodeMapper;
    private final JudgeNodeTokenMapper tokenMapper;
    private final JudgeSecurityProperties securityProperties;
    private final FormalJudgeTokenService formalJudgeTokenService;
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

        JudgeNodeToken tokenRecord = new JudgeNodeToken();
        tokenRecord.setTokenId(tokenId);
        tokenRecord.setNodeId(nodeId);
        tokenRecord.setNodeName(resolveNodeName(request, authCode));
        tokenRecord.setNodeType(JudgeNodeConstant.NODE_TYPE_TEMP);
        tokenRecord.setStatus(JudgeNodeConstant.TOKEN_ACTIVE);
        tokenRecord.setAuthCodeId(authCode.getId());
        tokenRecord.setExpireTime(expireTime);
        tokenMapper.insert(tokenRecord);

        String jwt = createJwt(tokenRecord, expireTime);
        JudgeTempTokenVo vo = new JudgeTempTokenVo();
        vo.setToken(jwt);
        vo.setTokenType(TOKEN_TYPE);
        vo.setNodeId(nodeId);
        vo.setTokenId(tokenId);
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
        return validateTempJwt(bearerToken);
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

    /**
     * @MethodName validateTempJwt
     * @Param token
     * @Description 验证临时jwt
     * @Return @return {@link JudgeNodeTokenValidationVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private JudgeNodeTokenValidationVo validateTempJwt(String token) {
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

        tokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, tokenId)
                .set(JudgeNodeToken::getLastUsedTime, LocalDateTime.now()));
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
        payload.put(CLAIM_NODE_TYPE, tokenRecord.getNodeType());
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
