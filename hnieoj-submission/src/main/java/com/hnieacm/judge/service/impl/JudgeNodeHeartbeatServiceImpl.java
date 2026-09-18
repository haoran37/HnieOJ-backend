package com.hnieacm.judge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.constant.JudgeNodeConstant;
import com.hnieacm.judge.dto.JudgeNodeHeartbeatRequest;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.properties.JudgeSecurityProperties;
import com.hnieacm.judge.service.JudgeNodeHeartbeatService;
import com.hnieacm.judge.service.JudgeNodeSecurityService;
import com.hnieacm.judge.vo.JudgeNodeIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题节点心跳服务实现：nodeId 只取自验证身份，服务端核准并发/模式上报不能提高
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeNodeHeartbeatServiceImpl implements JudgeNodeHeartbeatService {

    private static final String DEFAULT_JUDGE_MODE = "default";
    private static final String MODE_SEPARATOR = ",";
    private static final Set<String> FIXED_JUDGE_MODES = Set.of("default", "spj", "interactive");
    private static final int MAX_RUNNING_TASKS = 100000;
    private static final int MAX_CONCURRENCY = 10000;
    private static final int MAX_CPU_CORE = 4096;
    private static final int MAX_CACHE_PROBLEM_COUNT = 1000000;
    private static final long MAX_STORAGE_BYTES = 1L << 60;

    private final JudgeNodeSecurityService judgeNodeSecurityService;
    private final JudgeNodeTokenMapper judgeNodeTokenMapper;
    private final JudgeSecurityProperties securityProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordHeartbeat(String judgeToken, String authorizationHeader, JudgeNodeHeartbeatRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "心跳请求不能为空");
        }
        JudgeNodeIdentity identity = judgeNodeSecurityService.resolveIdentity(judgeToken, authorizationHeader);
        validateRequest(request, identity);

        JudgeNodeToken token = judgeNodeTokenMapper.selectOne(new LambdaQueryWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, identity.getTokenId())
                .last("limit 1"));
        if (token == null || !JudgeNodeConstant.TOKEN_ACTIVE.equals(token.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "判题节点凭证不可用");
        }

        Integer approved = token.getApprovedMaxConcurrency() == null
                ? token.getMaxConcurrency() : token.getApprovedMaxConcurrency();
        Integer effectiveMaxConcurrency = clampConcurrency(approved, request.getMaxConcurrency());

        // 模式集合由服务端授权固定，心跳上报不能扩大；draining 只能由节点主动置为 true，
        // 管理员设置的 draining 不会被后续普通心跳清除（恢复接单走管理员 draining=false 接口）。
        LambdaUpdateWrapper<JudgeNodeToken> wrapper = new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, identity.getTokenId())
                .eq(JudgeNodeToken::getStatus, JudgeNodeConstant.TOKEN_ACTIVE)
                .set(JudgeNodeToken::getNodeName, request.getNodeName())
                .set(JudgeNodeToken::getMaxConcurrency, effectiveMaxConcurrency)
                .set(JudgeNodeToken::getRunningTasks, request.getRunningTasks())
                .set(JudgeNodeToken::getCpuCore, request.getCpuCore())
                .set(JudgeNodeToken::getVersion, request.getVersion())
                .set(JudgeNodeToken::getCacheUsedBytes, request.getCacheUsedBytes())
                .set(JudgeNodeToken::getCacheProblemCount, request.getCacheProblemCount())
                .set(JudgeNodeToken::getDiskTotalBytes, request.getDiskTotalBytes())
                .set(JudgeNodeToken::getDiskFreeBytes, request.getDiskFreeBytes())
                .set(JudgeNodeToken::getLastHeartbeatTime, LocalDateTime.now());
        if (Boolean.TRUE.equals(request.getDraining())) {
            wrapper.set(JudgeNodeToken::getDraining, Boolean.TRUE);
        }
        int updated = judgeNodeTokenMapper.update(null, wrapper);
        if (updated <= 0) {
            throw new BizException(ResultCode.FORBIDDEN, "判题节点凭证不可用");
        }
        log.debug("Judge node heartbeat updated, nodeId: {}, tokenId: {}, effectiveMaxConcurrency: {}",
                identity.getNodeId(), identity.getTokenId(), effectiveMaxConcurrency);
    }

    @Override
    public boolean hasActiveNodeForMode(String judgeMode) {
        String normalizedJudgeMode = StrUtil.trimToNull(judgeMode) == null
                ? DEFAULT_JUDGE_MODE : judgeMode.trim().toLowerCase();
        if (!FIXED_JUDGE_MODES.contains(normalizedJudgeMode)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime activeAfter = now.minusSeconds(securityProperties.getNodeActiveTimeoutSeconds());
        List<JudgeNodeToken> tokens = judgeNodeTokenMapper.selectList(new LambdaQueryWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getStatus, JudgeNodeConstant.TOKEN_ACTIVE)
                .eq(JudgeNodeToken::getDraining, Boolean.FALSE)
                .gt(JudgeNodeToken::getExpireTime, now)
                .and(wrapper -> wrapper.isNull(JudgeNodeToken::getAuthorizationUntil)
                        .or().gt(JudgeNodeToken::getAuthorizationUntil, now))
                .ge(JudgeNodeToken::getLastHeartbeatTime, activeAfter));
        return tokens.stream().anyMatch(token -> supportsMode(token.getSupportedJudgeModes(), normalizedJudgeMode));
    }

    private void validateRequest(JudgeNodeHeartbeatRequest request, JudgeNodeIdentity identity) {
        request.setNodeId(StrUtil.trimToNull(request.getNodeId()));
        request.setNodeName(StrUtil.trimToNull(request.getNodeName()));
        request.setNodeType(StrUtil.trimToNull(request.getNodeType()));
        request.setVersion(StrUtil.trimToNull(request.getVersion()));
        request.setSupportedJudgeModes(normalizeSupportedJudgeModes(request.getSupportedJudgeModes()));
        if (request.getNodeId() != null && !request.getNodeId().equals(identity.getNodeId())) {
            throw new BizException(ResultCode.FORBIDDEN, "nodeId 与凭证不一致");
        }
        // nodeId 以服务端验证身份为准，不信任请求声称
        request.setNodeId(identity.getNodeId());
        if (request.getNodeType() != null && !request.getNodeType().equals(identity.getNodeType())) {
            throw new BizException(ResultCode.FORBIDDEN, "nodeType 与凭证不一致");
        }
        request.setNodeType(identity.getNodeType());
        validateRange(request.getMaxConcurrency(), "maxConcurrency", MAX_CONCURRENCY);
        validateRange(request.getRunningTasks(), "runningTasks", MAX_RUNNING_TASKS);
        validateRange(request.getCpuCore(), "cpuCore", MAX_CPU_CORE);
        validateRange(request.getCacheUsedBytes(), "cacheUsedBytes", MAX_STORAGE_BYTES);
        validateRange(request.getCacheProblemCount(), "cacheProblemCount", MAX_CACHE_PROBLEM_COUNT);
        validateRange(request.getDiskTotalBytes(), "diskTotalBytes", MAX_STORAGE_BYTES);
        validateRange(request.getDiskFreeBytes(), "diskFreeBytes", MAX_STORAGE_BYTES);
        if (request.getDiskTotalBytes() != null && request.getDiskFreeBytes() != null
                && request.getDiskFreeBytes() > request.getDiskTotalBytes()) {
            throw new BizException(ResultCode.BAD_REQUEST, "diskFreeBytes 不能大于 diskTotalBytes");
        }
    }

    private Integer clampConcurrency(Integer approved, Integer reported) {
        if (approved == null || approved <= 0) {
            if (reported == null || reported <= 0) {
                return null;
            }
            return reported;
        }
        if (reported == null || reported <= 0) {
            return approved;
        }
        return Math.min(approved, reported);
    }

    private List<String> normalizeSupportedJudgeModes(List<String> supportedJudgeModes) {
        if (supportedJudgeModes == null || supportedJudgeModes.isEmpty()) {
            return List.of(DEFAULT_JUDGE_MODE);
        }
        List<String> normalizedModes = supportedJudgeModes.stream()
                .map(item -> StrUtil.trimToNull(item) == null ? DEFAULT_JUDGE_MODE : item.trim().toLowerCase())
                .peek(mode -> {
                    if (!FIXED_JUDGE_MODES.contains(mode)) {
                        throw new BizException(ResultCode.BAD_REQUEST, "不支持的判题模式: " + mode);
                    }
                })
                .distinct()
                .toList();
        return normalizedModes.isEmpty() ? List.of(DEFAULT_JUDGE_MODE) : normalizedModes;
    }

    private boolean supportsMode(String supportedJudgeModes, String judgeMode) {
        String normalizedModes = StrUtil.blankToDefault(supportedJudgeModes, DEFAULT_JUDGE_MODE);
        return Arrays.stream(normalizedModes.split(MODE_SEPARATOR))
                .map(StrUtil::trimToNull)
                .filter(item -> item != null)
                .anyMatch(item -> item.equalsIgnoreCase(judgeMode));
    }

    private void validateRange(Integer value, String fieldName, int max) {
        if (value != null && (value < 0 || value > max)) {
            throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 不合法");
        }
    }

    private void validateRange(Long value, String fieldName, long max) {
        if (value != null && (value < 0 || value > max)) {
            throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 不合法");
        }
    }
}
