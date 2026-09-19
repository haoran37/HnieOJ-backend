package com.hnieacm.judge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.judge.NodeProtocolConstants;
import com.hnieacm.common.judge.NodeSignatureCodec;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.dto.NodeKeyRotationPrepareRequest;
import com.hnieacm.judge.entity.JudgeNodeKey;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeKeyMapper;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.properties.NodeSecurityProperties;
import com.hnieacm.judge.service.NodeIdentityService;
import com.hnieacm.judge.service.NodeKeyRotationService;
import com.hnieacm.judge.service.SignedCaller;
import com.hnieacm.judge.vo.NodeKeyRotationVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 密钥轮换实现。
 *
 * <p>prepare 使用当前 ACTIVE 密钥授权 + 新密钥证明；confirm 由新密钥签名激活。
 * 同一 rotationId 相同内容幂等、不同内容冲突；revoke 状态永远优先。
 * 所有状态迁移在节点行锁保护下串行执行，过期 pending 的清理使用独立事务真实提交，
 * 避免异常回滚导致后续轮换被永久挡住。</p>
 *
 * @author Codex
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeKeyRotationServiceImpl implements NodeKeyRotationService {

    private static final ZoneOffset ZONE = ZoneOffset.ofHours(8);

    private final JudgeNodeKeyMapper keyMapper;
    private final JudgeNodeTokenMapper tokenMapper;
    private final NodeIdentityService nodeIdentityService;
    private final NodeSecurityProperties nodeSecurityProperties;
    private final PlatformTransactionManager transactionManager;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NodeKeyRotationVo prepare(SignedCaller caller, NodeKeyRotationPrepareRequest request) {
        JudgeNodeToken node = lockAndValidateCaller(caller);
        // 轮换发起必须使用当前 ACTIVE 密钥，GRACE 密钥不具轮换权。
        JudgeNodeKey activeKey = requireActiveKey(caller.nodeId(), caller.keyId());
        NodeSignatureCodec.requireValid(request.getNewPublicKey(), NodeSignatureCodec.canonical(
                NodeProtocolConstants.DOMAIN_ROTATE_PREPARE,
                nodeSecurityProperties.getAudience(),
                caller.nodeId(),
                request.getRotationId(),
                request.getNewPublicKey()), request.getNewKeyProof());

        JudgeNodeKey existing = findByRotation(caller.nodeId(), request.getRotationId());
        if (existing != null) {
            boolean sameKey = Objects.equals(existing.getPublicKey(), request.getNewPublicKey());
            boolean pendingOrActive = NodeProtocolConstants.KEY_STATUS_PENDING.equals(existing.getStatus())
                    || NodeProtocolConstants.KEY_STATUS_ACTIVE.equals(existing.getStatus());
            if (sameKey && pendingOrActive) {
                return toVo(existing);
            }
            if (sameKey && NodeProtocolConstants.KEY_STATUS_EXPIRED.equals(existing.getStatus())) {
                // 过期 pending 允许用同一 rotationId 重新发起，避免客户端被永久卡死。
                String nonce = randomToken(32);
                LocalDateTime expiresAt = toLocal(System.currentTimeMillis()
                        + nodeSecurityProperties.getRotationPendingTtlSeconds() * 1000L);
                int revived = keyMapper.update(null, new LambdaUpdateWrapper<JudgeNodeKey>()
                        .eq(JudgeNodeKey::getKeyId, existing.getKeyId())
                        .eq(JudgeNodeKey::getStatus, NodeProtocolConstants.KEY_STATUS_EXPIRED)
                        .set(JudgeNodeKey::getStatus, NodeProtocolConstants.KEY_STATUS_PENDING)
                        .set(JudgeNodeKey::getConfirmNonce, nonce)
                        .set(JudgeNodeKey::getExpiresAt, expiresAt)
                        .set(JudgeNodeKey::getPreviousKeyId, activeKey.getKeyId())
                        .set(JudgeNodeKey::getGmtModified, LocalDateTime.now()));
                if (revived == 0) {
                    throw new BizException(ResultCode.CONFLICT, "轮换状态已变化，请重试");
                }
                return toVo(keyMapper.selectOne(new LambdaQueryWrapper<JudgeNodeKey>()
                        .eq(JudgeNodeKey::getKeyId, existing.getKeyId())));
            }
            throw new BizException(ResultCode.CONFLICT, "同一 rotationId 已存在不同密钥");
        }
        expireStalePending(caller.nodeId());
        Long pending = keyMapper.selectCount(new LambdaQueryWrapper<JudgeNodeKey>()
                .eq(JudgeNodeKey::getNodeId, caller.nodeId())
                .eq(JudgeNodeKey::getStatus, NodeProtocolConstants.KEY_STATUS_PENDING));
        if (pending != null && pending > 0) {
            throw new BizException(ResultCode.CONFLICT, "已存在待确认的轮换");
        }
        long now = System.currentTimeMillis();
        LocalDateTime expiresAt = toLocal(now + nodeSecurityProperties.getRotationPendingTtlSeconds() * 1000L);
        JudgeNodeKey key = new JudgeNodeKey();
        key.setKeyId(UUID.randomUUID().toString());
        key.setNodeId(caller.nodeId());
        key.setPublicKey(request.getNewPublicKey());
        key.setPublicKeyHash(NodeSignatureCodec.sha256Hex(request.getNewPublicKey()));
        key.setStatus(NodeProtocolConstants.KEY_STATUS_PENDING);
        key.setRotationId(request.getRotationId());
        key.setConfirmNonce(randomToken(32));
        key.setExpiresAt(expiresAt);
        key.setPreviousKeyId(activeKey.getKeyId());
        key.setGmtCreate(LocalDateTime.now());
        key.setGmtModified(LocalDateTime.now());
        keyMapper.insert(key);
        return toVo(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NodeKeyRotationVo confirm(SignedCaller caller, String rotationId, String signature) {
        lockAndValidateCaller(caller);
        JudgeNodeKey key = findByRotation(caller.nodeId(), rotationId);
        if (key == null) {
            throw new BizException(ResultCode.NOT_FOUND, "轮换不存在");
        }
        if (NodeProtocolConstants.KEY_STATUS_ACTIVE.equals(key.getStatus())) {
            // 幂等分支同样必须校验相同的新密钥证明，避免伪造重复确认。
            verifyConfirmProof(caller.nodeId(), rotationId, key, signature);
            return toVo(key);
        }
        if (!NodeProtocolConstants.KEY_STATUS_PENDING.equals(key.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "轮换状态不允许确认");
        }
        if (key.getExpiresAt() != null && toEpoch(key.getExpiresAt()) <= System.currentTimeMillis()) {
            // 过期清理必须在独立事务中真实提交，否则会随本次异常一起回滚。
            markExpiredInNewTransaction(key.getKeyId());
            throw new BizException(ResultCode.FORBIDDEN, "轮换已过期");
        }
        verifyConfirmProof(caller.nodeId(), rotationId, key, signature);

        LocalDateTime graceUntil = toLocal(System.currentTimeMillis()
                + nodeSecurityProperties.getRotationGraceSeconds() * 1000L);
        JudgeNodeKey previous = key.getPreviousKeyId() == null ? null
                : keyMapper.selectOne(new LambdaQueryWrapper<JudgeNodeKey>()
                        .eq(JudgeNodeKey::getKeyId, key.getPreviousKeyId()));
        // revoke 永远优先：被吊销密钥不允许借轮换复活。
        if (previous != null && NodeProtocolConstants.KEY_STATUS_REVOKED.equals(previous.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "原密钥已吊销");
        }
        int activated = keyMapper.update(null, new LambdaUpdateWrapper<JudgeNodeKey>()
                .eq(JudgeNodeKey::getKeyId, key.getKeyId())
                .eq(JudgeNodeKey::getStatus, NodeProtocolConstants.KEY_STATUS_PENDING)
                .set(JudgeNodeKey::getStatus, NodeProtocolConstants.KEY_STATUS_ACTIVE)
                .set(JudgeNodeKey::getActivatedAt, LocalDateTime.now())
                .set(JudgeNodeKey::getExpiresAt, null));
        if (activated == 0) {
            throw new BizException(ResultCode.CONFLICT, "轮换状态已变化，请重试");
        }
        if (previous != null && !Objects.equals(previous.getKeyId(), key.getKeyId())) {
            keyMapper.update(null, new LambdaUpdateWrapper<JudgeNodeKey>()
                    .eq(JudgeNodeKey::getKeyId, previous.getKeyId())
                    .eq(JudgeNodeKey::getStatus, NodeProtocolConstants.KEY_STATUS_ACTIVE)
                    .set(JudgeNodeKey::getStatus, NodeProtocolConstants.KEY_STATUS_GRACE)
                    .set(JudgeNodeKey::getExpiresAt, graceUntil));
        }
        // 正常轮换不提升 access_version：旧密钥在 grace 内仍需继续业务，
        // 只有吊销/降权等紧急路径才提升版本强制旧令牌失效。
        tokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, caller.nodeId())
                .set(JudgeNodeToken::getActiveKeyId, key.getKeyId())
                .set(JudgeNodeToken::getGmtModified, LocalDateTime.now()));

        JudgeNodeKey refreshed = keyMapper.selectOne(new LambdaQueryWrapper<JudgeNodeKey>()
                .eq(JudgeNodeKey::getKeyId, key.getKeyId()));
        return toVo(refreshed == null ? key : refreshed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NodeKeyRotationVo query(SignedCaller caller, String rotationId) {
        lockAndValidateCaller(caller);
        JudgeNodeKey key = findByRotation(caller.nodeId(), rotationId);
        if (key == null) {
            throw new BizException(ResultCode.NOT_FOUND, "轮换不存在");
        }
        if (NodeProtocolConstants.KEY_STATUS_PENDING.equals(key.getStatus())
                && key.getExpiresAt() != null
                && toEpoch(key.getExpiresAt()) <= System.currentTimeMillis()) {
            keyMapper.update(null, new LambdaUpdateWrapper<JudgeNodeKey>()
                    .eq(JudgeNodeKey::getKeyId, key.getKeyId())
                    .eq(JudgeNodeKey::getStatus, NodeProtocolConstants.KEY_STATUS_PENDING)
                    .set(JudgeNodeKey::getStatus, NodeProtocolConstants.KEY_STATUS_EXPIRED));
            key.setStatus(NodeProtocolConstants.KEY_STATUS_EXPIRED);
        }
        return toVo(key);
    }

    /**
     * 加节点行锁并校验调用方仍是权威会话、密钥有效、accessVersion/epoch 未变。
     */
    private JudgeNodeToken lockAndValidateCaller(SignedCaller caller) {
        JudgeNodeToken locked = tokenMapper.lockNode(caller.nodeId());
        if (locked == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "节点不存在");
        }
        return nodeIdentityService.requireSessionOwner(caller.nodeId(), caller.keyId(),
                caller.sessionEpoch(), caller.accessVersion(), true);
    }

    private void verifyConfirmProof(String nodeId, String rotationId, JudgeNodeKey key, String signature) {
        NodeSignatureCodec.requireValid(key.getPublicKey(), NodeSignatureCodec.canonical(
                NodeProtocolConstants.DOMAIN_ROTATE_CONFIRM,
                nodeSecurityProperties.getAudience(),
                nodeId,
                rotationId,
                key.getKeyId(),
                key.getConfirmNonce()), signature);
    }

    private void expireStalePending(String nodeId) {
        keyMapper.update(null, new LambdaUpdateWrapper<JudgeNodeKey>()
                .eq(JudgeNodeKey::getNodeId, nodeId)
                .eq(JudgeNodeKey::getStatus, NodeProtocolConstants.KEY_STATUS_PENDING)
                .isNotNull(JudgeNodeKey::getExpiresAt)
                .lt(JudgeNodeKey::getExpiresAt, LocalDateTime.now())
                .set(JudgeNodeKey::getStatus, NodeProtocolConstants.KEY_STATUS_EXPIRED));
    }

    private void markExpiredInNewTransaction(String keyId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> keyMapper.update(null, new LambdaUpdateWrapper<JudgeNodeKey>()
                .eq(JudgeNodeKey::getKeyId, keyId)
                .eq(JudgeNodeKey::getStatus, NodeProtocolConstants.KEY_STATUS_PENDING)
                .set(JudgeNodeKey::getStatus, NodeProtocolConstants.KEY_STATUS_EXPIRED)));
    }

    private JudgeNodeKey requireActiveKey(String nodeId, String keyId) {
        JudgeNodeKey key = keyMapper.selectOne(new LambdaQueryWrapper<JudgeNodeKey>()
                .eq(JudgeNodeKey::getKeyId, keyId)
                .eq(JudgeNodeKey::getNodeId, nodeId));
        if (key == null || !NodeProtocolConstants.KEY_STATUS_ACTIVE.equals(key.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "密钥轮换需要当前 ACTIVE 密钥");
        }
        return key;
    }

    private JudgeNodeKey findByRotation(String nodeId, String rotationId) {
        return keyMapper.selectOne(new LambdaQueryWrapper<JudgeNodeKey>()
                .eq(JudgeNodeKey::getNodeId, nodeId)
                .eq(JudgeNodeKey::getRotationId, rotationId));
    }

    private NodeKeyRotationVo toVo(JudgeNodeKey key) {
        return new NodeKeyRotationVo(key.getRotationId(), key.getKeyId(), key.getStatus(),
                key.getPublicKey(), key.getConfirmNonce(), toEpoch(key.getExpiresAt()),
                resolveGraceUntil(key));
    }

    /**
     * 持久化解析 grace 截止：GRACE 密钥取自身 expires_at；ACTIVE 密钥取被其轮换、
     * 仍处于 GRACE 的旧密钥 expires_at，保证 query/confirm 返回一致的 graceUntil。
     */
    private Long resolveGraceUntil(JudgeNodeKey key) {
        if (NodeProtocolConstants.KEY_STATUS_GRACE.equals(key.getStatus())) {
            return toEpoch(key.getExpiresAt());
        }
        if (NodeProtocolConstants.KEY_STATUS_ACTIVE.equals(key.getStatus()) && key.getPreviousKeyId() != null) {
            JudgeNodeKey previous = keyMapper.selectOne(new LambdaQueryWrapper<JudgeNodeKey>()
                    .eq(JudgeNodeKey::getKeyId, key.getPreviousKeyId()));
            if (previous != null && NodeProtocolConstants.KEY_STATUS_GRACE.equals(previous.getStatus())) {
                return toEpoch(previous.getExpiresAt());
            }
        }
        return null;
    }

    private String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        secureRandom.nextBytes(buffer);
        return Base64.getEncoder().encodeToString(buffer);
    }

    private static LocalDateTime toLocal(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZONE);
    }

    private static long toEpoch(LocalDateTime time) {
        return time == null ? 0L : time.toInstant(ZONE).toEpochMilli();
    }
}
