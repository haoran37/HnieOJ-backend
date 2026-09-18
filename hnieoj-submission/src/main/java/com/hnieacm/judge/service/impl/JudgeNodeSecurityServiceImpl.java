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
import com.hnieacm.judge.dto.CreateFormalJudgeTokenRequest;
import com.hnieacm.judge.dto.CreateJudgeAuthCodeRequest;
import com.hnieacm.judge.dto.ExchangeJudgeTempTokenRequest;
import com.hnieacm.judge.dto.ValidateJudgeNodeTokenRequest;
import com.hnieacm.judge.entity.JudgeNodeAuthCode;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeAuthCodeMapper;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.properties.JudgeSecurityProperties;
import com.hnieacm.judge.service.JudgeNodeSecurityService;
import com.hnieacm.judge.vo.JudgeAuthCodeVo;
import com.hnieacm.judge.vo.JudgeNodeIdentity;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLAIM_TOKEN_ID = "tokenId";
    private static final String CLAIM_NODE_ID = "nodeId";
    private static final String CLAIM_NODE_TYPE = "type";
    private static final String CLAIM_EXPIRE_TIME = "exp";
    private static final String CLAIM_ISSUED_AT = "iat";
    private static final String DEFAULT_SECRET_MARK = "replace_me";
    private static final String DEFAULT_JUDGE_MODE = "default";
    private static final String MODE_SEPARATOR = ",";
    private static final Set<String> FIXED_JUDGE_MODES = Set.of("default", "spj", "interactive");
    private static final long HEARTBEAT_ONLINE_TIMEOUT_SECONDS = 90;
    private static final int MAX_CONCURRENCY_LIMIT = 10000;

    private final JudgeNodeAuthCodeMapper authCodeMapper;
    private final JudgeNodeTokenMapper tokenMapper;
    private final JudgeSecurityProperties securityProperties;
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
        authCode.setNodeName(trimToNull(request.getNodeName()));
        authCode.setCreatedBy(StpUtil.getLoginIdAsString());
        authCode.setRemark(trimToNull(request.getRemark()));
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
     * @Description 临时节点首次接入：用授权码兑换独立节点凭证
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

        // 与数据库 datetime(0) 精度对齐，保证 authorizationUntil 上限判定稳定
        LocalDateTime expireTime = now.plusSeconds(tempTokenTtlSeconds()).withNano(0);
        JudgeNodeToken tokenRecord = new JudgeNodeToken();
        tokenRecord.setTokenId(newId());
        tokenRecord.setNodeId(newId());
        tokenRecord.setNodeName(resolveNodeName(request, authCode));
        tokenRecord.setNodeType(JudgeNodeConstant.NODE_TYPE_TEMP);
        tokenRecord.setStatus(JudgeNodeConstant.TOKEN_ACTIVE);
        tokenRecord.setAuthCodeId(authCode.getId());
        tokenRecord.setExpireTime(expireTime);
        // 临时节点最晚授权期限：续期不得超过首次接入授予的期限
        tokenRecord.setAuthorizationUntil(expireTime);
        // 服务端在签发时固定临时节点的核准额度与允许模式，节点心跳不能自行扩大
        tokenRecord.setApprovedMaxConcurrency(tempNodeDefaultMaxConcurrency());
        tokenRecord.setMaxConcurrency(tempNodeDefaultMaxConcurrency());
        tokenRecord.setSupportedJudgeModes(joinSupportedJudgeModes(securityProperties.getTempNodeAllowedModes()));
        tokenRecord.setDraining(Boolean.FALSE);
        tokenMapper.insert(tokenRecord);

        return toCredentialVo(tokenRecord, expireTime);
    }

    /**
     * @MethodName issueFormalToken
     * @Param request
     * @Description 管理员为正式节点签发独立凭证
     * @Return @return {@link JudgeTempTokenVo }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeTempTokenVo issueFormalToken(CreateFormalJudgeTokenRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        Integer maxConcurrency = request.getMaxConcurrency();
        if (maxConcurrency == null || maxConcurrency <= 0 || maxConcurrency > MAX_CONCURRENCY_LIMIT) {
            throw new BizException(ResultCode.BAD_REQUEST, "maxConcurrency 不合法");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusSeconds(formalTokenTtlSeconds()).withNano(0);
        JudgeNodeToken tokenRecord = new JudgeNodeToken();
        tokenRecord.setTokenId(newId());
        tokenRecord.setNodeId(newId());
        tokenRecord.setNodeName(trimToNull(request.getNodeName()));
        tokenRecord.setNodeType(JudgeNodeConstant.NODE_TYPE_FORMAL);
        tokenRecord.setStatus(JudgeNodeConstant.TOKEN_ACTIVE);
        tokenRecord.setExpireTime(expireTime);
        tokenRecord.setApprovedMaxConcurrency(maxConcurrency);
        tokenRecord.setMaxConcurrency(maxConcurrency);
        tokenRecord.setSupportedJudgeModes(joinSupportedJudgeModes(request.getSupportedJudgeModes()));
        tokenRecord.setRunningTasks(0L);
        tokenRecord.setDraining(Boolean.FALSE);        tokenMapper.insert(tokenRecord);

        log.info("Formal judge node token issued, nodeId: {}, tokenId: {}, maxConcurrency: {}",
                tokenRecord.getNodeId(), tokenRecord.getTokenId(), maxConcurrency);
        return toCredentialVo(tokenRecord, expireTime);
    }

    /**
     * @MethodName renewToken
     * @Param authorizationHeader
     * @Description 稳定续期：保持 nodeId/tokenId，临时节点不得超过 authorizationUntil
     * @Return @return {@link JudgeTempTokenVo }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeTempTokenVo renewToken(String authorizationHeader) {
        String bearerToken = extractBearerToken(authorizationHeader);
        JudgeNodeToken tokenRecord = loadActiveToken(bearerToken);
        if (tokenRecord == null) {
            throw new BizException(ResultCode.FORBIDDEN, "判题节点凭证无效");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime;
        if (JudgeNodeConstant.NODE_TYPE_TEMP.equals(tokenRecord.getNodeType())) {
            LocalDateTime authorizationUntil = tokenRecord.getAuthorizationUntil();
            if (authorizationUntil == null || !authorizationUntil.isAfter(now)) {
                throw new BizException(ResultCode.FORBIDDEN, "临时节点授权期限已到");
            }
            LocalDateTime candidate = now.plusSeconds(tempTokenTtlSeconds()).withNano(0);
            expireTime = candidate.isAfter(authorizationUntil) ? authorizationUntil : candidate;
        } else {
            expireTime = now.plusSeconds(formalTokenTtlSeconds()).withNano(0);
        }

        int updated = tokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getId, tokenRecord.getId())
                .eq(JudgeNodeToken::getStatus, JudgeNodeConstant.TOKEN_ACTIVE)
                .set(JudgeNodeToken::getExpireTime, expireTime));
        if (updated <= 0) {
            // 并发撤销/过期竞争下不能返回虚假的续期成功
            throw new BizException(ResultCode.FORBIDDEN, "判题节点凭证已失效");
        }
        tokenRecord.setExpireTime(expireTime);
        log.info("Judge node token renewed, nodeId: {}, tokenId: {}, expireTime: {}, authorizationUntil: {}",
                tokenRecord.getNodeId(), tokenRecord.getTokenId(), expireTime, tokenRecord.getAuthorizationUntil());
        return toCredentialVo(tokenRecord, expireTime);
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
                .ne(JudgeNodeToken::getStatus, JudgeNodeConstant.TOKEN_REVOKED)
                .set(JudgeNodeToken::getStatus, JudgeNodeConstant.TOKEN_REVOKED)
                .set(JudgeNodeToken::getRevokedBy, StpUtil.getLoginIdAsString())
                .set(JudgeNodeToken::getRevokedTime, LocalDateTime.now()));
        if (updated <= 0) {
            throw new BizException(ResultCode.NOT_FOUND, "Token 不存在或已撤销");
        }
        log.info("Judge node token revoked, tokenId: {}", normalizedTokenId);
    }

    /**
     * @MethodName updateDraining
     * @Param tokenId
     * @Param draining
     * @Description 设置节点 draining：停止领取新任务但可完成在途任务
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDraining(String tokenId, Boolean draining) {
        String normalizedTokenId = trimToNull(tokenId);
        if (normalizedTokenId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "tokenId 不能为空");
        }
        int updated = tokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, normalizedTokenId)
                .set(JudgeNodeToken::getDraining, Boolean.TRUE.equals(draining)));
        if (updated <= 0) {
            throw new BizException(ResultCode.NOT_FOUND, "Token 不存在");
        }
    }

    /**
     * @MethodName resolveIdentity
     * @Param judgeToken
     * @Param authorizationHeader
     * @Description 解析运行期节点身份，旧共享 X-Judge-Token 一律拒绝
     * @Return @return {@link JudgeNodeIdentity }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Override
    public JudgeNodeIdentity resolveIdentity(String judgeToken, String authorizationHeader) {
        JudgeNodeToken tokenRecord = loadActiveToken(extractBearerToken(authorizationHeader));
        if (tokenRecord == null) {
            throw new BizException(ResultCode.FORBIDDEN, "判题节点凭证无效");
        }
        tokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getId, tokenRecord.getId())
                .set(JudgeNodeToken::getLastUsedTime, LocalDateTime.now()));
        return toIdentity(tokenRecord);
    }

    /**
     * @MethodName validateToken
     * @Param request
     * @Description 兼容内部校验接口
     * @Return @return {@link JudgeNodeTokenValidationVo }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Override
    public JudgeNodeTokenValidationVo validateToken(ValidateJudgeNodeTokenRequest request) {
        try {
            JudgeNodeToken tokenRecord = loadActiveToken(extractBearerToken(
                    request == null ? null : request.getBearerToken()));
            if (tokenRecord == null) {
                return invalidValidation();
            }
            JudgeNodeTokenValidationVo vo = new JudgeNodeTokenValidationVo();
            vo.setValid(Boolean.TRUE);
            vo.setNodeType(tokenRecord.getNodeType());
            vo.setNodeId(tokenRecord.getNodeId());
            vo.setTokenId(tokenRecord.getTokenId());
            return vo;
        } catch (Exception e) {
            log.debug("Validate judge node token failed: {}", e.getMessage());
            return invalidValidation();
        }
    }

    /**
     * @MethodName loadActiveToken
     * @Param bearerToken
     * @Description 校验 JWT 签名/有效期与数据库记录，返回有效节点 Token
     * @Return @return {@link JudgeNodeToken }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    private JudgeNodeToken loadActiveToken(String bearerToken) {
        if (StrUtil.isBlank(bearerToken)) {
            return null;
        }
        JWT jwt;
        try {
            if (!JWTUtil.verify(bearerToken, jwtKey())) {
                return null;
            }
            jwt = JWTUtil.parseToken(bearerToken).setKey(jwtKey());
        } catch (Exception e) {
            log.debug("Parse judge node jwt failed: {}", e.getMessage());
            return null;
        }
        String tokenId = payloadToString(jwt.getPayload(CLAIM_TOKEN_ID));
        String nodeId = payloadToString(jwt.getPayload(CLAIM_NODE_ID));
        String nodeType = payloadToString(jwt.getPayload(CLAIM_NODE_TYPE));
        Long jwtExpireMillis = parseLong(jwt.getPayload(CLAIM_EXPIRE_TIME));
        if (StrUtil.isBlank(tokenId) || StrUtil.isBlank(nodeId)
                || !JudgeNodeConstant.NODE_TYPE_TEMP.equals(nodeType)
                && !JudgeNodeConstant.NODE_TYPE_FORMAL.equals(nodeType)
                || jwtExpireMillis == null || jwtExpireMillis < System.currentTimeMillis()) {
            return null;
        }

        JudgeNodeToken tokenRecord = tokenMapper.selectOne(new LambdaQueryWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, tokenId)
                .last("limit 1"));
        if (tokenRecord == null || !JudgeNodeConstant.TOKEN_ACTIVE.equals(tokenRecord.getStatus())) {
            return null;
        }
        if (!nodeId.equals(tokenRecord.getNodeId()) || !nodeType.equals(tokenRecord.getNodeType())) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (tokenRecord.getExpireTime() == null || !tokenRecord.getExpireTime().isAfter(now)) {
            markTokenExpiredIfNeeded(tokenRecord);
            return null;
        }
        if (JudgeNodeConstant.NODE_TYPE_TEMP.equals(tokenRecord.getNodeType())) {
            LocalDateTime authorizationUntil = tokenRecord.getAuthorizationUntil();
            if (authorizationUntil == null || !authorizationUntil.isAfter(now)) {
                return null;
            }
        }
        return tokenRecord;
    }

    private JudgeNodeTokenValidationVo invalidValidation() {
        JudgeNodeTokenValidationVo vo = new JudgeNodeTokenValidationVo();
        vo.setValid(Boolean.FALSE);
        return vo;
    }

    private JudgeNodeIdentity toIdentity(JudgeNodeToken tokenRecord) {
        JudgeNodeIdentity identity = new JudgeNodeIdentity();
        identity.setNodeId(tokenRecord.getNodeId());
        identity.setTokenId(tokenRecord.getTokenId());
        identity.setNodeType(tokenRecord.getNodeType());
        identity.setMaxConcurrency(resolveMaxConcurrency(tokenRecord));
        identity.setRunningTasks(tokenRecord.getRunningTasks());
        identity.setDraining(Boolean.TRUE.equals(tokenRecord.getDraining()));
        identity.setAuthorizationUntil(tokenRecord.getAuthorizationUntil());
        identity.setExpireTime(tokenRecord.getExpireTime());
        identity.setSupportedJudgeModes(splitSupportedJudgeModes(tokenRecord.getSupportedJudgeModes()));
        return identity;
    }

    private Integer resolveMaxConcurrency(JudgeNodeToken tokenRecord) {
        if (tokenRecord.getApprovedMaxConcurrency() != null && tokenRecord.getApprovedMaxConcurrency() > 0) {
            return tokenRecord.getApprovedMaxConcurrency();
        }
        if (tokenRecord.getMaxConcurrency() != null && tokenRecord.getMaxConcurrency() > 0) {
            return tokenRecord.getMaxConcurrency();
        }
        return 1;
    }

    private JudgeTempTokenVo toCredentialVo(JudgeNodeToken tokenRecord, LocalDateTime expireTime) {
        JudgeTempTokenVo vo = new JudgeTempTokenVo();
        vo.setToken(createJwt(tokenRecord, expireTime));
        vo.setTokenType(TOKEN_TYPE);
        vo.setNodeId(tokenRecord.getNodeId());
        vo.setTokenId(tokenRecord.getTokenId());
        vo.setExpireTime(expireTime);
        return vo;
    }

    /**
     * @MethodName createJwt
     * @Param tokenRecord
     * @Param expireTime
     * @Description 创建逐节点 JWT
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

    private byte[] jwtKey() {
        if (StrUtil.isBlank(securityProperties.getJwtSecret())
                || securityProperties.getJwtSecret().contains(DEFAULT_SECRET_MARK)) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "判题节点 JWT 密钥未正确配置");
        }
        return securityProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
    }

    private String generateAuthCode() {
        byte[] bytes = new byte[AUTH_CODE_RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String hash(String value) {
        return SecureUtil.sha256(value);
    }

    private void markAuthCodeExpired(JudgeNodeAuthCode authCode) {
        authCodeMapper.update(null, new LambdaUpdateWrapper<JudgeNodeAuthCode>()
                .eq(JudgeNodeAuthCode::getId, authCode.getId())
                .set(JudgeNodeAuthCode::getStatus, JudgeNodeConstant.AUTH_CODE_EXPIRED));
    }

    private void markTokenExpiredIfNeeded(JudgeNodeToken tokenRecord) {
        if (tokenRecord != null && JudgeNodeConstant.TOKEN_ACTIVE.equals(tokenRecord.getStatus())
                && tokenRecord.getExpireTime() != null && tokenRecord.getExpireTime().isBefore(LocalDateTime.now())) {
            tokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                    .eq(JudgeNodeToken::getId, tokenRecord.getId())
                    .set(JudgeNodeToken::getStatus, JudgeNodeConstant.TOKEN_EXPIRED));
        }
    }

    private String resolveNodeName(ExchangeJudgeTempTokenRequest request, JudgeNodeAuthCode authCode) {
        String nodeName = trimToNull(request.getNodeName());
        if (nodeName != null) {
            return nodeName;
        }
        return authCode.getNodeName();
    }

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
        vo.setApprovedMaxConcurrency(token.getApprovedMaxConcurrency());
        vo.setRunningTasks(token.getRunningTasks());
        vo.setCpuCore(token.getCpuCore());
        vo.setVersion(token.getVersion());
        vo.setSupportedJudgeModes(splitSupportedJudgeModes(token.getSupportedJudgeModes()));
        vo.setCacheUsedBytes(token.getCacheUsedBytes());
        vo.setCacheProblemCount(token.getCacheProblemCount());
        vo.setDiskTotalBytes(token.getDiskTotalBytes());
        vo.setDiskFreeBytes(token.getDiskFreeBytes());
        vo.setAuthorizationUntil(token.getAuthorizationUntil());
        vo.setDraining(Boolean.TRUE.equals(token.getDraining()));
        vo.setGmtCreate(token.getGmtCreate());
        return vo;
    }

    private Boolean isOnline(JudgeNodeToken token) {
        if (token.getLastHeartbeatTime() == null || !JudgeNodeConstant.TOKEN_ACTIVE.equals(token.getStatus())) {
            return false;
        }
        long timeoutSeconds = securityProperties.getNodeActiveTimeoutSeconds() > 0
                ? securityProperties.getNodeActiveTimeoutSeconds() : HEARTBEAT_ONLINE_TIMEOUT_SECONDS;
        return token.getLastHeartbeatTime().isAfter(LocalDateTime.now().minusSeconds(timeoutSeconds));
    }

    private List<String> splitSupportedJudgeModes(String supportedJudgeModes) {
        String normalizedModes = StrUtil.blankToDefault(supportedJudgeModes, DEFAULT_JUDGE_MODE);
        return Arrays.stream(normalizedModes.split(MODE_SEPARATOR))
                .map(StrUtil::trimToNull)
                .filter(item -> item != null)
                .toList();
    }

    private String joinSupportedJudgeModes(List<String> supportedJudgeModes) {
        if (supportedJudgeModes == null || supportedJudgeModes.isEmpty()) {
            return DEFAULT_JUDGE_MODE;
        }
        String joined = supportedJudgeModes.stream()
                .map(StrUtil::trimToNull)
                .filter(item -> item != null)
                .map(String::toLowerCase)
                .peek(this::validateJudgeMode)
                .distinct()
                .reduce((a, b) -> a + MODE_SEPARATOR + b)
                .orElse(DEFAULT_JUDGE_MODE);
        return joined.isBlank() ? DEFAULT_JUDGE_MODE : joined;
    }

    /**
     * @MethodName validateJudgeMode
     * @Param judgeMode
     * @Description 只允许固定集合 default/spj/interactive，禁止任意 mode 静默映射回 default
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    private void validateJudgeMode(String judgeMode) {
        if (!FIXED_JUDGE_MODES.contains(judgeMode)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的判题模式: " + judgeMode);
        }
    }

    private int tempNodeDefaultMaxConcurrency() {
        int value = securityProperties.getTempNodeDefaultMaxConcurrency();
        if (value <= 0 || value > MAX_CONCURRENCY_LIMIT) {
            return 1;
        }
        return value;
    }

    private String extractBearerToken(String authorizationHeader) {
        String normalized = trimToNull(authorizationHeader);
        if (normalized == null) {
            return null;
        }
        if (normalized.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return StrUtil.trimToNull(normalized.substring(BEARER_PREFIX.length()));
        }
        return null;
    }

    private long tempTokenTtlSeconds() {
        long value = securityProperties.getTempTokenTtlSeconds();
        return value <= 0 ? 7200L : value;
    }

    private long formalTokenTtlSeconds() {
        long value = securityProperties.getFormalTokenTtlSeconds();
        return value <= 0 ? 2592000L : value;
    }

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

    private String payloadToString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private String trimToNull(String value) {
        return StrUtil.trimToNull(value);
    }
}
