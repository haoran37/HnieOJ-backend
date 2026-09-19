package com.hnieacm.judge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.judge.NodeProtocolConstants;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.dto.UpdateNodePolicyRequest;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.service.JudgeNodeLifecycleService;
import com.hnieacm.judge.vo.JudgeNodeTokenVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 节点生命周期管理实现：全部在节点行锁内串行，与认证/轮换/数据平面保持同一锁顺序。
 *
 * @author Codex
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeNodeLifecycleServiceImpl implements JudgeNodeLifecycleService {

    private static final Set<String> ALLOWED_JUDGE_MODES = Set.of("default", "spj", "interactive");
    private static final int MIN_WEIGHT = 1;
    private static final int MAX_WEIGHT = 100;
    private static final ZoneOffset ZONE = ZoneOffset.ofHours(8);

    private final JudgeNodeTokenMapper tokenMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeNodeTokenVo drain(String nodeId) {
        JudgeNodeToken node = lockExisting(nodeId);
        if (NodeProtocolConstants.NODE_STATUS_REVOKED.equals(node.getStatus())
                || NodeProtocolConstants.NODE_STATUS_DISABLED.equals(node.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "节点当前状态不可排空");
        }
        // 普通排空不提升 accessVersion：在途续租/结果回传必须继续有效。
        tokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, node.getTokenId())
                .set(JudgeNodeToken::getStatus, NodeProtocolConstants.NODE_STATUS_DRAINING)
                .set(JudgeNodeToken::getDraining, true)
                .set(JudgeNodeToken::getGmtModified, LocalDateTime.now()));
        return JudgeNodeTokenVo.from(tokenMapper.selectById(node.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeNodeTokenVo disable(String nodeId) {
        JudgeNodeToken node = lockExisting(nodeId);
        if (NodeProtocolConstants.NODE_STATUS_REVOKED.equals(node.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "节点已吊销，不能改为禁用");
        }
        int nextVersion = nextAccessVersion(node);
        tokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, node.getTokenId())
                .set(JudgeNodeToken::getStatus, NodeProtocolConstants.NODE_STATUS_DISABLED)
                .set(JudgeNodeToken::getDraining, true)
                .set(JudgeNodeToken::getAccessVersion, nextVersion)
                .set(JudgeNodeToken::getGmtModified, LocalDateTime.now()));
        return JudgeNodeTokenVo.from(tokenMapper.selectById(node.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeNodeTokenVo enable(String nodeId) {
        JudgeNodeToken node = lockExisting(nodeId);
        // 吊销是终态，enable 不得复活；硬到期（expireTime/authorizationUntil）也不能自行续命。
        if (NodeProtocolConstants.NODE_STATUS_REVOKED.equals(node.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "节点已吊销，不能启用");
        }
        long now = System.currentTimeMillis();
        if (node.getExpireTime() != null && toEpoch(node.getExpireTime()) <= now) {
            throw new BizException(ResultCode.FORBIDDEN, "节点身份已到期，不能启用");
        }
        if (node.getAuthorizationUntil() != null && toEpoch(node.getAuthorizationUntil()) <= now) {
            throw new BizException(ResultCode.FORBIDDEN, "节点授权已到期，不能启用");
        }
        int nextVersion = nextAccessVersion(node);
        tokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, node.getTokenId())
                .set(JudgeNodeToken::getStatus, NodeProtocolConstants.NODE_STATUS_ACTIVE)
                .set(JudgeNodeToken::getDraining, false)
                .set(JudgeNodeToken::getAccessVersion, nextVersion)
                .set(JudgeNodeToken::getGmtModified, LocalDateTime.now()));
        return JudgeNodeTokenVo.from(tokenMapper.selectById(node.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeNodeTokenVo updatePolicy(String nodeId, UpdateNodePolicyRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "策略请求不能为空");
        }
        JudgeNodeToken node = lockExisting(nodeId);
        if (NodeProtocolConstants.NODE_STATUS_REVOKED.equals(node.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "节点已吊销，不能更新策略");
        }
        boolean permissionChanged = false;
        String modes = node.getSupportedJudgeModes();
        if (request.getSupportedJudgeModes() != null) {
            modes = String.join(",", normalizeModes(request.getSupportedJudgeModes()));
            permissionChanged = true;
        }
        Integer maxConcurrency = node.getMaxConcurrency();
        if (request.getMaxConcurrency() != null) {
            maxConcurrency = request.getMaxConcurrency();
            permissionChanged = true;
        }
        Integer weight = node.getWeight();
        if (request.getWeight() != null) {
            if (request.getWeight() < MIN_WEIGHT || request.getWeight() > MAX_WEIGHT) {
                throw new BizException(ResultCode.BAD_REQUEST, "weight 超出允许范围");
            }
            weight = request.getWeight();
        }
        LocalDateTime authorizationUntil = node.getAuthorizationUntil();
        if (request.getAuthorizationUntil() != null) {
            long deadline = request.getAuthorizationUntil();
            if (deadline <= System.currentTimeMillis()) {
                throw new BizException(ResultCode.BAD_REQUEST, "authorizationUntil 必须大于当前时间");
            }
            authorizationUntil = toLocal(deadline);
            permissionChanged = true;
        }
        LambdaUpdateWrapper<JudgeNodeToken> update = new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, node.getTokenId())
                .set(JudgeNodeToken::getSupportedJudgeModes, modes)
                .set(JudgeNodeToken::getMaxConcurrency, maxConcurrency)
                .set(JudgeNodeToken::getWeight, weight == null ? 10 : weight)
                .set(JudgeNodeToken::getAuthorizationUntil, authorizationUntil)
                .set(JudgeNodeToken::getGmtModified, LocalDateTime.now());
        if (permissionChanged) {
            // 权限/授权变更必须提升 accessVersion，使旧短期授权按协议失效。
            update.set(JudgeNodeToken::getAccessVersion, nextAccessVersion(node));
        }
        tokenMapper.update(null, update);
        return JudgeNodeTokenVo.from(tokenMapper.selectById(node.getId()));
    }

    private JudgeNodeToken lockExisting(String nodeId) {
        String normalized = StrUtil.trimToNull(nodeId);
        if (normalized == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "tokenId 不能为空");
        }
        JudgeNodeToken node = tokenMapper.lockNode(normalized);
        if (node == null) {
            throw new BizException(ResultCode.NOT_FOUND, "Token 不存在");
        }
        return node;
    }

    private int nextAccessVersion(JudgeNodeToken node) {
        int current = node.getAccessVersion() == null ? 0 : node.getAccessVersion();
        return current + 1;
    }

    private List<String> normalizeModes(List<String> modes) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String mode : modes) {
            String trimmed = StrUtil.trimToNull(mode);
            if (trimmed == null || !ALLOWED_JUDGE_MODES.contains(trimmed)) {
                throw new BizException(ResultCode.BAD_REQUEST, "supportedJudgeModes 含非法值");
            }
            normalized.add(trimmed);
        }
        if (normalized.isEmpty()) {
            normalized.add("default");
        }
        return new ArrayList<>(normalized);
    }

    private static LocalDateTime toLocal(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZONE);
    }

    private static long toEpoch(LocalDateTime time) {
        return time == null ? 0L : time.toInstant(ZONE).toEpochMilli();
    }
}
