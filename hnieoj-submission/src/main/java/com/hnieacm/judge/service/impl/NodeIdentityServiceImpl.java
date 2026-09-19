package com.hnieacm.judge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.judge.NodeAccessTokenService;
import com.hnieacm.common.judge.NodeProtocolConstants;
import com.hnieacm.common.judge.NodeSignatureCodec;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.dto.CreateNodeBootstrapRequest;
import com.hnieacm.judge.dto.NodeAuthChallenge;
import com.hnieacm.judge.dto.NodeAuthChallengeVo;
import com.hnieacm.judge.dto.NodeAuthResult;
import com.hnieacm.judge.dto.NodeEnrollRequest;
import com.hnieacm.judge.dto.NodeEnrollmentChallenge;
import com.hnieacm.judge.dto.NodeEnrollmentChallengeRequest;
import com.hnieacm.judge.dto.NodePolicy;
import com.hnieacm.judge.dto.NodeRuntimeMetrics;
import com.hnieacm.judge.entity.JudgeNodeAuthCode;
import com.hnieacm.judge.entity.JudgeNodeKey;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeAuthCodeMapper;
import com.hnieacm.judge.mapper.JudgeNodeKeyMapper;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.properties.JudgeSecurityProperties;
import com.hnieacm.judge.properties.NodeSecurityProperties;
import com.hnieacm.judge.service.NodeIdentityService;
import com.hnieacm.judge.vo.NodeBootstrapVo;
import com.hnieacm.judge.vo.NodeEnrollVo;
import com.hnieacm.judge.vo.NodeEnrollmentChallengeVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 节点身份服务实现：Ed25519 Bootstrap + 注册 + WSS 认证 + 会话纪元。
 *
 * <p>数据库是 sessionEpoch / 节点状态 / 授权截止的权威；Redis 只保存一次性挑战与 nonce。
 * 所有 Redis 失败均 fail-closed，不吞异常放行。</p>
 *
 * @author Codex
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeIdentityServiceImpl implements NodeIdentityService {

    private static final Set<String> ALLOWED_JUDGE_MODES = Set.of("default", "spj", "interactive");
    private static final long MAX_BOOTSTRAP_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000;
    private static final long MAX_NODE_TTL_MILLIS = 10L * 365 * 24 * 60 * 60 * 1000;
    private static final long CHALLENGE_RATE_WINDOW_SECONDS = 60L;
    private static final int CHALLENGE_RATE_MAX = 30;
    private static final int CHALLENGE_RATE_IP_MAX = 60;
    private static final int MAX_CLIENT_IP_LENGTH = 64;
    private static final ZoneOffset ZONE = ZoneOffset.ofHours(8);

    /**
     * 原子自增并保证 TTL：避免 INCR 成功后 EXPIRE 失败遗留永不过期的键。
     */
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]); "
                    + "local t = redis.call('TTL', KEYS[1]); "
                    + "if t < 0 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return c;", Long.class);

    private final JudgeNodeAuthCodeMapper authCodeMapper;
    private final JudgeNodeTokenMapper tokenMapper;
    private final JudgeNodeKeyMapper keyMapper;
    private final NodeSecurityProperties nodeSecurityProperties;
    private final JudgeSecurityProperties judgeSecurityProperties;
    private final NodeAccessTokenService nodeAccessTokenService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NodeBootstrapVo createBootstrap(CreateNodeBootstrapRequest request) {
        long now = System.currentTimeMillis();
        String nodeType = request.getNodeType();
        if (!NodeProtocolConstants.NODE_TYPE_FORMAL.equals(nodeType)
                && !NodeProtocolConstants.NODE_TYPE_TEMP.equals(nodeType)) {
            throw new BizException(ResultCode.BAD_REQUEST, "nodeType 非法");
        }
        long expiresAt = request.getExpiresAt() == null ? 0L : request.getExpiresAt();
        if (expiresAt <= now || expiresAt > now + MAX_BOOTSTRAP_TTL_MILLIS) {
            throw new BizException(ResultCode.BAD_REQUEST, "expiresAt 超出允许范围");
        }
        Long authorizationUntil = request.getAuthorizationUntil();
        if (NodeProtocolConstants.NODE_TYPE_TEMP.equals(nodeType)) {
            if (authorizationUntil == null || authorizationUntil <= now) {
                throw new BizException(ResultCode.BAD_REQUEST, "temp 节点的 authorizationUntil 必须大于当前时间");
            }
        }
        if (authorizationUntil != null && authorizationUntil <= now) {
            throw new BizException(ResultCode.BAD_REQUEST, "authorizationUntil 必须大于当前时间");
        }
        List<String> modes = normalizeModes(request.getSupportedJudgeModes());
        int weight = request.getWeight() == null ? 10 : request.getWeight();

        String plaintext = randomToken(32);
        JudgeNodeAuthCode authCode = new JudgeNodeAuthCode();
        authCode.setCodeHash(NodeSignatureCodec.sha256Hex(plaintext));
        authCode.setNodeName(trimToNull(request.getNodeName()));
        authCode.setCreatedBy(currentLoginIdOrNull());
        authCode.setRemark(trimToNull(request.getRemark()));
        authCode.setMaxExchangeCount(1);
        authCode.setUsedCount(0);
        authCode.setStatus(NodeProtocolConstants.BOOTSTRAP_STATUS_ENABLED);
        authCode.setExpireTime(toLocal(expiresAt));
        authCode.setNodeType(nodeType);
        authCode.setAuthorizationUntil(authorizationUntil == null ? null : toLocal(authorizationUntil));
        authCode.setPolicyJson(writePolicy(modes, request.getMaxConcurrency(), weight, authorizationUntil));
        authCode.setGmtCreate(LocalDateTime.now());
        authCode.setGmtModified(LocalDateTime.now());
        authCodeMapper.insert(authCode);
        return new NodeBootstrapVo(authCode.getId(), plaintext, nodeType, expiresAt);
    }

    @Override
    public NodeEnrollmentChallengeVo createEnrollmentChallenge(NodeEnrollmentChallengeRequest request, String clientIp) {
        String digest = NodeSignatureCodec.sha256Hex(request.getBootstrapToken());
        limitChallengeRate(digest, clientIp);
        JudgeNodeAuthCode authCode = findByDigest(digest);
        if (authCode == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "Bootstrap 凭据无效");
        }
        JudgeNodeToken existing = findByEnrollmentId(request.getEnrollmentId());
        boolean recovery = matchesRegistration(existing, authCode, request);
        boolean enabled = NodeProtocolConstants.BOOTSTRAP_STATUS_ENABLED.equals(authCode.getStatus())
                && authCode.getExpireTime() != null
                && authCode.getExpireTime().isAfter(LocalDateTime.now());
        if (!recovery && !enabled) {
            throw new BizException(ResultCode.UNAUTHORIZED, "Bootstrap 凭据已消费或已过期");
        }
        long now = System.currentTimeMillis();
        long expiresAt = now + nodeSecurityProperties.getChallengeTtlSeconds() * 1000L;
        String challengeId = UUID.randomUUID().toString();
        String nonce = randomToken(32);

        NodeEnrollmentChallenge challenge = new NodeEnrollmentChallenge();
        challenge.setAuthCodeId(authCode.getId());
        challenge.setBootstrapDigest(digest);
        challenge.setEnrollmentId(request.getEnrollmentId());
        challenge.setNodeName(request.getNodeName());
        challenge.setPublicKey(request.getPublicKey());
        challenge.setNonce(nonce);
        challenge.setAudience(nodeSecurityProperties.getAudience());
        challenge.setExpiresAt(expiresAt);
        challenge.setRecoveryNodeId(recovery ? existing.getTokenId() : null);
        storeJson(NodeProtocolConstants.REDIS_ENROLL_CHALLENGE_PREFIX + challengeId, challenge,
                nodeSecurityProperties.getChallengeTtlSeconds());

        return new NodeEnrollmentChallengeVo(challengeId, nonce, nodeSecurityProperties.getAudience(),
                expiresAt, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NodeEnrollVo enroll(NodeEnrollRequest request) {
        String digest = NodeSignatureCodec.sha256Hex(request.getBootstrapToken());
        JudgeNodeAuthCode authCode = findByDigest(digest);
        if (authCode == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "Bootstrap 凭据无效");
        }
        NodeEnrollmentChallenge challenge = takeChallenge(
                NodeProtocolConstants.REDIS_ENROLL_CHALLENGE_PREFIX + request.getChallengeId(),
                NodeEnrollmentChallenge.class);
        if (challenge == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "注册挑战不存在或已被使用");
        }
        verifyEnrollmentChallenge(challenge, request, digest);
        NodeSignatureCodec.requireValid(request.getPublicKey(), NodeSignatureCodec.canonical(
                NodeProtocolConstants.DOMAIN_ENROLL,
                challenge.getAudience(),
                request.getChallengeId(),
                challenge.getNonce(),
                request.getEnrollmentId(),
                request.getNodeName(),
                request.getPublicKey(),
                digest), request.getSignature());

        JudgeNodeToken existing = findByEnrollmentId(request.getEnrollmentId());
        if (existing != null) {
            if (!matchesRegistration(existing, authCode, request)) {
                throw new BizException(ResultCode.FORBIDDEN, "该 enrollment 已绑定其他密钥或节点");
            }
            return toEnrollVo(existing);
        }

        String nodeId = UUID.randomUUID().toString();
        String keyId = UUID.randomUUID().toString();
        String publicKeyHash = NodeSignatureCodec.sha256Hex(request.getPublicKey());
        int consumed = authCodeMapper.consumeBootstrap(authCode.getId(), request.getEnrollmentId(),
                publicKeyHash, nodeId);
        if (consumed == 0) {
            JudgeNodeToken raced = findByEnrollmentId(request.getEnrollmentId());
            if (raced != null && matchesRegistration(raced, authCode, request)) {
                return toEnrollVo(raced);
            }
            throw new BizException(ResultCode.CONFLICT, "Bootstrap 并发争用失败");
        }
        try {
            JudgeNodeToken node = insertNode(authCode, request, nodeId, keyId, publicKeyHash);
            insertInitialKey(nodeId, keyId, request.getPublicKey(), publicKeyHash);
            return toEnrollVo(node);
        } catch (DuplicateKeyException e) {
            JudgeNodeToken raced = findByEnrollmentId(request.getEnrollmentId());
            if (raced != null && matchesRegistration(raced, authCode, request)) {
                return toEnrollVo(raced);
            }
            throw new BizException(ResultCode.CONFLICT, "节点注册冲突");
        }
    }

    @Override
    public NodeAuthChallengeVo createAuthChallenge(String connectionId, String nodeId, Long sessionEpoch,
                                                   String requestId, boolean refresh) {
        long now = System.currentTimeMillis();
        long expiresAt = now + nodeSecurityProperties.getChallengeTtlSeconds() * 1000L;
        String challengeId = UUID.randomUUID().toString();
        NodeAuthChallenge challenge = new NodeAuthChallenge();
        challenge.setChallengeId(challengeId);
        challenge.setRequestId(requestId);
        challenge.setConnectionId(connectionId);
        challenge.setNonce(randomToken(32));
        challenge.setAudience(nodeSecurityProperties.getAudience());
        challenge.setExpiresAt(expiresAt);
        challenge.setRefresh(refresh);
        challenge.setNodeId(refresh ? nodeId : null);
        challenge.setSessionEpoch(refresh ? sessionEpoch : null);
        storeJson(NodeProtocolConstants.REDIS_AUTH_CHALLENGE_PREFIX + challengeId, challenge,
                nodeSecurityProperties.getChallengeTtlSeconds());
        return new NodeAuthChallengeVo(challengeId, challenge.getNonce(),
                nodeSecurityProperties.getAudience(), expiresAt, now, requestId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NodeAuthResult authenticate(String connectionId, String nodeId, String keyId,
                                       String challengeId, String requestId, String signature) {
        NodeAuthChallenge challenge = takeChallenge(
                NodeProtocolConstants.REDIS_AUTH_CHALLENGE_PREFIX + challengeId, NodeAuthChallenge.class);
        if (challenge == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "认证挑战不存在或已被使用");
        }
        if (!Objects.equals(challenge.getConnectionId(), connectionId)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "认证挑战与连接不匹配");
        }
        // 物理挑战与 requestId 绑定：AUTH_RESPONSE 必须回传挑战发起时的 requestId。
        if (requestId == null || !Objects.equals(challenge.getRequestId(), requestId)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "认证挑战与请求 ID 不匹配");
        }
        if (!nodeSecurityProperties.getAudience().equals(challenge.getAudience())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "认证挑战受众不匹配");
        }
        if (challenge.isRefresh()) {
            // 刷新挑战绑定创建时的物理会话节点与纪元：旧连接不能换绑其他节点，
            // 也不能读取被接管后的最新纪元伪装成当前会话。
            if (challenge.getNodeId() == null || !challenge.getNodeId().equals(nodeId)
                    || challenge.getSessionEpoch() == null) {
                throw new BizException(ResultCode.UNAUTHORIZED, "刷新挑战与连接绑定不匹配");
            }
        }
        // TOCTOU 修复：先对节点行加排他锁，锁后再读最新节点/密钥/版本/纪元；
        // 与 claim/rotation/admin 保持“先节点行锁、后读密钥”的一致锁顺序。
        JudgeNodeToken node = tokenMapper.lockNode(nodeId);
        if (node == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "节点不存在");
        }
        requireServiceableState(node);
        JudgeNodeKey key = requireUsableKey(nodeId, keyId, true);
        NodeSignatureCodec.requireValid(key.getPublicKey(), NodeSignatureCodec.canonical(
                NodeProtocolConstants.DOMAIN_AUTH,
                challenge.getAudience(),
                challengeId,
                challenge.getNonce(),
                nodeId,
                keyId), signature);
        // 锁内重新计算时间：挑战过期判定基于获得锁之后的最新时刻。
        long now = System.currentTimeMillis();
        if (challenge.getExpiresAt() <= now) {
            throw new BizException(ResultCode.UNAUTHORIZED, "认证挑战已过期");
        }

        long epoch;
        long currentEpoch = node.getSessionEpoch() == null ? 0L : node.getSessionEpoch();
        if (challenge.isRefresh()) {
            if (currentEpoch != challenge.getSessionEpoch()) {
                throw new BizException(ResultCode.FORBIDDEN, "会话已被接管");
            }
            epoch = currentEpoch;
        } else {
            // 行锁串行化接管：并发新连接各自获得互不相同的新纪元。
            epoch = currentEpoch + 1;
            if (tokenMapper.incrementSessionEpoch(nodeId) == 0) {
                throw new BizException(ResultCode.FORBIDDEN, "节点当前不可接管");
            }
        }
        long hardDeadline = resolveHardDeadline(node, key);
        int accessVersion = node.getAccessVersion() == null ? 0 : node.getAccessVersion();
        NodeAccessTokenService.IssuedToken issued = nodeAccessTokenService.issue(
                nodeId, keyId, key.getPublicKeyHash(), accessVersion, epoch, hardDeadline);
        long heartbeatInterval = Math.max(5000L, judgeSecurityProperties.getNodeActiveTimeoutSeconds() * 1000L / 3);
        NodeAuthResult result = new NodeAuthResult(nodeId, keyId, issued.token(), issued.expiresAt(), epoch, now,
                node.getMaxConcurrency(), parseModes(node.getSupportedJudgeModes()),
                node.getAuthorizationUntil() == null ? null : toEpoch(node.getAuthorizationUntil()),
                heartbeatInterval, accessVersion, node.getWeight(),
                node.getStatus(), Boolean.TRUE.equals(node.getDraining()));
        return result;
    }

    @Override
    public long currentSessionEpoch(String nodeId) {
        JudgeNodeToken node = findNode(nodeId);
        if (node == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "节点不存在");
        }
        return node.getSessionEpoch() == null ? 0L : node.getSessionEpoch();
    }

    @Override
    public JudgeNodeToken findNode(String nodeId) {
        return findNodeById(nodeId);
    }

    @Override
    public JudgeNodeToken requireSessionOwner(String nodeId, String keyId, long sessionEpoch,
                                              Integer accessVersion, boolean allowGrace) {
        JudgeNodeToken node = requireServiceableNode(nodeId);
        validateSessionOwner(node, keyId, sessionEpoch, accessVersion, allowGrace);
        return node;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeNodeToken lockSessionOwner(String nodeId, String keyId, long sessionEpoch,
                                           Integer accessVersion, boolean allowGrace) {
        // 先锁节点行：持锁后读到的状态在事务提交前不会被子系统策略变更改掉。
        JudgeNodeToken node = tokenMapper.lockNode(nodeId);
        if (node == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "节点不存在");
        }
        requireServiceableState(node);
        validateSessionOwner(node, keyId, sessionEpoch, accessVersion, allowGrace);
        return node;
    }

    private void validateSessionOwner(JudgeNodeToken node, String keyId, long sessionEpoch,
                                      Integer accessVersion, boolean allowGrace) {
        requireUsableKey(node.getTokenId(), keyId, allowGrace);
        long current = node.getSessionEpoch() == null ? 0L : node.getSessionEpoch();
        if (current != sessionEpoch) {
            throw new BizException(ResultCode.FORBIDDEN, "会话已被接管");
        }
        if (accessVersion != null) {
            int nodeVersion = node.getAccessVersion() == null ? 0 : node.getAccessVersion();
            if (nodeVersion != accessVersion) {
                throw new BizException(ResultCode.FORBIDDEN, "节点访问版本已变更");
            }
        }
    }

    @Override
    public void touchHeartbeat(String nodeId, long sessionEpoch) {
        touchHeartbeat(nodeId, sessionEpoch, null);
    }

    @Override
    public void touchHeartbeat(String nodeId, long sessionEpoch, NodeRuntimeMetrics metrics) {
        if (tokenMapper.touchSessionHeartbeat(nodeId, sessionEpoch) == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "会话已被接管");
        }
        if (metrics == null || !metrics.hasAny()) {
            return;
        }
        LambdaUpdateWrapper<JudgeNodeToken> update = new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, nodeId)
                .eq(JudgeNodeToken::getSessionEpoch, sessionEpoch)
                .in(JudgeNodeToken::getStatus,
                        NodeProtocolConstants.NODE_STATUS_ACTIVE, NodeProtocolConstants.NODE_STATUS_DRAINING);
        // 只写运行指标列；绝不触碰 max_concurrency / supported_judge_modes / status / draining。
        if (metrics.getCpuCore() != null) {
            update.set(JudgeNodeToken::getCpuCore, metrics.getCpuCore());
        }
        if (metrics.getVersion() != null) {
            update.set(JudgeNodeToken::getVersion, metrics.getVersion());
        }
        if (metrics.getRunningTasks() != null) {
            update.set(JudgeNodeToken::getRunningTasks, metrics.getRunningTasks());
        }
        if (metrics.getCacheUsedBytes() != null) {
            update.set(JudgeNodeToken::getCacheUsedBytes, metrics.getCacheUsedBytes());
        }
        if (metrics.getCacheProblemCount() != null) {
            update.set(JudgeNodeToken::getCacheProblemCount, metrics.getCacheProblemCount());
        }
        if (metrics.getDiskTotalBytes() != null) {
            update.set(JudgeNodeToken::getDiskTotalBytes, metrics.getDiskTotalBytes());
        }
        if (metrics.getDiskFreeBytes() != null) {
            update.set(JudgeNodeToken::getDiskFreeBytes, metrics.getDiskFreeBytes());
        }
        tokenMapper.update(null, update);
    }

    @Override
    public JudgeNodeToken requireServiceableNode(String nodeId) {
        JudgeNodeToken node = findNodeById(nodeId);
        if (node == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "节点不存在");
        }
        requireServiceableState(node);
        return node;
    }

    private void requireServiceableState(JudgeNodeToken node) {
        if (!NodeProtocolConstants.NODE_STATUS_ACTIVE.equals(node.getStatus())
                && !NodeProtocolConstants.NODE_STATUS_DRAINING.equals(node.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "节点状态不允许业务操作");
        }
        long now = System.currentTimeMillis();
        if (node.getExpireTime() != null && toEpoch(node.getExpireTime()) <= now) {
            throw new BizException(ResultCode.FORBIDDEN, "节点身份已到期");
        }
        if (node.getAuthorizationUntil() != null && toEpoch(node.getAuthorizationUntil()) <= now) {
            throw new BizException(ResultCode.FORBIDDEN, "节点授权已到期");
        }
    }

    @Override
    public JudgeNodeKey requireUsableKey(String nodeId, String keyId, boolean allowGrace) {
        JudgeNodeKey key = keyMapper.selectOne(new LambdaQueryWrapper<JudgeNodeKey>()
                .eq(JudgeNodeKey::getKeyId, keyId)
                .eq(JudgeNodeKey::getNodeId, nodeId));
        if (key == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "密钥不存在");
        }
        boolean active = NodeProtocolConstants.KEY_STATUS_ACTIVE.equals(key.getStatus());
        boolean grace = NodeProtocolConstants.KEY_STATUS_GRACE.equals(key.getStatus());
        boolean graceAllowed = allowGrace && grace;
        if (!active && !graceAllowed) {
            throw new BizException(ResultCode.FORBIDDEN, "密钥状态不允许该操作");
        }
        if (key.getExpiresAt() != null && toEpoch(key.getExpiresAt()) <= System.currentTimeMillis()) {
            throw new BizException(ResultCode.FORBIDDEN, "密钥已过期");
        }
        return key;
    }

    @Override
    public NodeAccessTokenService.ParsedToken parseAccessToken(String token) {
        try {
            return nodeAccessTokenService.parse(token);
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.UNAUTHORIZED, e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    private JudgeNodeToken insertNode(JudgeNodeAuthCode authCode, NodeEnrollRequest request,
                                      String nodeId, String keyId, String publicKeyHash) {
        NodePolicy policy = readPolicy(authCode.getPolicyJson());
        List<String> modes = policy.getSupportedJudgeModes() == null || policy.getSupportedJudgeModes().isEmpty()
                ? List.of("default") : policy.getSupportedJudgeModes();
        long now = System.currentTimeMillis();
        LocalDateTime authorizationUntil = authCode.getAuthorizationUntil();
        LocalDateTime expireTime = authorizationUntil != null
                ? authorizationUntil : toLocal(now + MAX_NODE_TTL_MILLIS);
        JudgeNodeToken node = new JudgeNodeToken();
        node.setTokenId(nodeId);
        node.setNodeId(nodeId);
        node.setNodeName(request.getNodeName());
        node.setNodeType(authCode.getNodeType());
        node.setStatus(NodeProtocolConstants.NODE_STATUS_ACTIVE);
        node.setAuthCodeId(authCode.getId());
        node.setProofType("ed25519");
        node.setPublicKey(request.getPublicKey());
        node.setPublicKeyHash(publicKeyHash);
        node.setExpireTime(expireTime);
        node.setMaxConcurrency(policy.getMaxConcurrency());
        node.setSupportedJudgeModes(String.join(",", modes));
        node.setWeight(policy.getWeight() == null ? 10 : policy.getWeight());
        node.setDraining(false);
        node.setRunningTasks(0L);
        node.setSessionEpoch(0L);
        node.setAccessVersion(1);
        node.setActiveKeyId(keyId);
        node.setAuthorizationUntil(authorizationUntil);
        node.setEnrollmentId(request.getEnrollmentId());
        node.setGmtCreate(LocalDateTime.now());
        node.setGmtModified(LocalDateTime.now());
        tokenMapper.insert(node);
        return node;
    }

    private void insertInitialKey(String nodeId, String keyId, String publicKey, String publicKeyHash) {
        JudgeNodeKey key = new JudgeNodeKey();
        key.setKeyId(keyId);
        key.setNodeId(nodeId);
        key.setPublicKey(publicKey);
        key.setPublicKeyHash(publicKeyHash);
        key.setStatus(NodeProtocolConstants.KEY_STATUS_ACTIVE);
        key.setActivatedAt(LocalDateTime.now());
        key.setGmtCreate(LocalDateTime.now());
        key.setGmtModified(LocalDateTime.now());
        keyMapper.insert(key);
    }

    private void verifyEnrollmentChallenge(NodeEnrollmentChallenge challenge, NodeEnrollRequest request,
                                           String digest) {
        long now = System.currentTimeMillis();
        if (challenge.getExpiresAt() <= now) {
            throw new BizException(ResultCode.UNAUTHORIZED, "注册挑战已过期");
        }
        if (!Objects.equals(challenge.getBootstrapDigest(), digest)
                || !Objects.equals(challenge.getEnrollmentId(), request.getEnrollmentId())
                || !Objects.equals(challenge.getNodeName(), request.getNodeName())
                || !Objects.equals(challenge.getPublicKey(), request.getPublicKey())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "注册挑战与请求不匹配");
        }
        if (!nodeSecurityProperties.getAudience().equals(challenge.getAudience())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "注册挑战受众不匹配");
        }
    }

    private boolean matchesRegistration(JudgeNodeToken node, JudgeNodeAuthCode authCode,
                                        NodeEnrollmentChallengeRequest request) {
        if (node == null || authCode == null) {
            return false;
        }
        return Objects.equals(node.getAuthCodeId(), authCode.getId())
                && Objects.equals(node.getEnrollmentId(), request.getEnrollmentId())
                && Objects.equals(node.getNodeName(), request.getNodeName())
                && Objects.equals(node.getPublicKeyHash(), NodeSignatureCodec.sha256Hex(request.getPublicKey()));
    }

    private long resolveHardDeadline(JudgeNodeToken node, JudgeNodeKey key) {
        long deadline = Long.MAX_VALUE;
        if (node.getExpireTime() != null) {
            deadline = Math.min(deadline, toEpoch(node.getExpireTime()));
        }
        if (node.getAuthorizationUntil() != null) {
            deadline = Math.min(deadline, toEpoch(node.getAuthorizationUntil()));
        }
        if (key.getExpiresAt() != null) {
            deadline = Math.min(deadline, toEpoch(key.getExpiresAt()));
        }
        return deadline;
    }

    private void limitChallengeRate(String digest, String clientIp) {
        // 独立 IP 桶：攻击者更换无效 digest 也无法绕过来源限流。
        String ipKey = NodeProtocolConstants.REDIS_BOOTSTRAP_RATE_PREFIX + "ip:" + normalizeClientIp(clientIp);
        if (incrementRateBucket(ipKey) > CHALLENGE_RATE_IP_MAX) {
            throw new BizException(ResultCode.FORBIDDEN, "注册挑战创建过于频繁");
        }
        // 独立 digest 桶：限制针对同一 Bootstrap 凭据的探测频率。
        String digestKey = NodeProtocolConstants.REDIS_BOOTSTRAP_RATE_PREFIX + "digest:" + digest;
        if (incrementRateBucket(digestKey) > CHALLENGE_RATE_MAX) {
            throw new BizException(ResultCode.FORBIDDEN, "注册挑战创建过于频繁");
        }
    }

    private long incrementRateBucket(String key) {
        Long count = stringRedisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(key),
                String.valueOf(CHALLENGE_RATE_WINDOW_SECONDS));
        if (count == null) {
            // Redis 未返回计数时 fail-closed，不静默放行。
            throw new BizException(ResultCode.INTERNAL_ERROR, "限流组件不可用");
        }
        return count.longValue();
    }

    private static String normalizeClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        String trimmed = clientIp.trim();
        return trimmed.length() > MAX_CLIENT_IP_LENGTH ? trimmed.substring(0, MAX_CLIENT_IP_LENGTH) : trimmed;
    }

    private JudgeNodeAuthCode findByDigest(String digest) {
        return authCodeMapper.selectOne(new LambdaQueryWrapper<JudgeNodeAuthCode>()
                .eq(JudgeNodeAuthCode::getCodeHash, digest));
    }

    private JudgeNodeToken findByEnrollmentId(String enrollmentId) {
        if (StrUtil.isBlank(enrollmentId)) {
            return null;
        }
        return tokenMapper.selectOne(new LambdaQueryWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getEnrollmentId, enrollmentId));
    }

    private JudgeNodeToken findNodeById(String nodeId) {
        if (StrUtil.isBlank(nodeId)) {
            return null;
        }
        return tokenMapper.selectOne(new LambdaQueryWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, nodeId));
    }

    private List<String> normalizeModes(List<String> modes) {
        if (modes == null || modes.isEmpty()) {
            return new ArrayList<>(List.of("default"));
        }
        List<String> normalized = new ArrayList<>();
        for (String mode : modes) {
            String trimmed = trimToNull(mode);
            if (trimmed == null || !ALLOWED_JUDGE_MODES.contains(trimmed)) {
                throw new BizException(ResultCode.BAD_REQUEST, "supportedJudgeModes 含非法值");
            }
            if (!normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }
        if (normalized.isEmpty()) {
            normalized.add("default");
        }
        return normalized;
    }

    private NodeEnrollVo toEnrollVo(JudgeNodeToken node) {
        return new NodeEnrollVo(node.getTokenId(), node.getActiveKeyId(), node.getNodeType(),
                node.getAuthorizationUntil() == null ? null : toEpoch(node.getAuthorizationUntil()),
                node.getMaxConcurrency(), parseModes(node.getSupportedJudgeModes()));
    }

    private List<String> parseModes(String modes) {
        if (StrUtil.isBlank(modes)) {
            return new ArrayList<>(List.of("default"));
        }
        return new ArrayList<>(Arrays.asList(modes.split(",")));
    }

    private String writePolicy(List<String> modes, Integer maxConcurrency, int weight, Long authorizationUntil) {
        NodePolicy policy = new NodePolicy();
        policy.setMaxConcurrency(maxConcurrency);
        policy.setSupportedJudgeModes(modes);
        policy.setWeight(weight);
        policy.setAuthorizationUntil(authorizationUntil);
        try {
            return objectMapper.writeValueAsString(policy);
        } catch (Exception e) {
            throw new IllegalStateException("节点策略序列化失败", e);
        }
    }

    private NodePolicy readPolicy(String json) {
        if (StrUtil.isBlank(json)) {
            NodePolicy fallback = new NodePolicy();
            fallback.setSupportedJudgeModes(new ArrayList<>(List.of("default")));
            fallback.setWeight(10);
            fallback.setMaxConcurrency(1);
            return fallback;
        }
        try {
            return objectMapper.readValue(json, NodePolicy.class);
        } catch (Exception e) {
            throw new IllegalStateException("节点策略解析失败", e);
        }
    }

    private <T> T takeChallenge(String key, Class<T> type) {
        String json = stringRedisTemplate.opsForValue().getAndDelete(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new BizException(ResultCode.UNAUTHORIZED, "挑战数据损坏");
        }
    }

    private void storeJson(String key, Object value, long ttlSeconds) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value),
                    Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            throw new IllegalStateException("挑战写入失败", e);
        }
    }

    private String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        secureRandom.nextBytes(buffer);
        return Base64.getEncoder().encodeToString(buffer);
    }

    private String currentLoginIdOrNull() {
        try {
            return cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDateTime toLocal(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZONE);
    }

    private static long toEpoch(LocalDateTime time) {
        return time == null ? 0L : time.toInstant(ZONE).toEpochMilli();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
