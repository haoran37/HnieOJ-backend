package com.hnieacm.judge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.judge.constant.JudgeNodeConstant;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.properties.JudgeSecurityProperties;
import com.hnieacm.judge.service.JudgeNodeOpsService;
import com.hnieacm.judge.vo.JudgeNodeOpsSummaryVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: Judge node operation service implementation.
 */
@Service
@RequiredArgsConstructor
public class JudgeNodeOpsServiceImpl implements JudgeNodeOpsService {

    private static final String DEFAULT_JUDGE_MODE = "default";
    private static final List<String> TOKEN_STATUSES = List.of(
            JudgeNodeConstant.TOKEN_ACTIVE,
            JudgeNodeConstant.TOKEN_REVOKED,
            JudgeNodeConstant.TOKEN_EXPIRED
    );
    private static final List<String> NODE_TYPES = List.of(
            JudgeNodeConstant.NODE_TYPE_FORMAL,
            JudgeNodeConstant.NODE_TYPE_TEMP
    );

    private final JudgeNodeTokenMapper judgeNodeTokenMapper;
    private final JudgeSecurityProperties securityProperties;

    @Override
    public JudgeNodeOpsSummaryVo summary() {
        LocalDateTime now = LocalDateTime.now();
        long heartbeatTimeoutSeconds = positiveOrDefault(securityProperties.getNodeActiveTimeoutSeconds(), 90L);
        long tempTokenExpireWarnSeconds = positiveOrDefault(securityProperties.getNodeExpireWarnSeconds(), 1800L);
        double diskFreeWarnRatio = positiveRatioOrDefault(securityProperties.getNodeDiskFreeWarnRatio(), 0.1D);
        List<JudgeNodeToken> nodes = judgeNodeTokenMapper.selectList(new LambdaQueryWrapper<JudgeNodeToken>()
                .orderByDesc(JudgeNodeToken::getLastHeartbeatTime)
                .orderByDesc(JudgeNodeToken::getGmtCreate)
                .orderByDesc(JudgeNodeToken::getId));

        JudgeNodeOpsSummaryVo vo = new JudgeNodeOpsSummaryVo();
        vo.setGeneratedAt(now);
        vo.setHeartbeatTimeoutSeconds(heartbeatTimeoutSeconds);
        vo.setTempTokenExpireWarnSeconds(tempTokenExpireWarnSeconds);
        vo.setDiskFreeWarnRatio(diskFreeWarnRatio);
        fillCounts(vo, nodes, now, heartbeatTimeoutSeconds, tempTokenExpireWarnSeconds, diskFreeWarnRatio);
        vo.setWarnings(buildWarnings(vo));
        vo.setHealthy(vo.getWarnings().isEmpty());
        return vo;
    }

    private void fillCounts(JudgeNodeOpsSummaryVo vo, List<JudgeNodeToken> nodes, LocalDateTime now,
                            long heartbeatTimeoutSeconds, long tempTokenExpireWarnSeconds,
                            double diskFreeWarnRatio) {
        Map<String, Long> statusCounts = initCountMap(TOKEN_STATUSES);
        Map<String, Long> nodeTypeCounts = initCountMap(NODE_TYPES);
        Map<String, Long> judgeModeOnlineCounts = new LinkedHashMap<>();
        long activeNodeCount = 0L;
        long onlineNodeCount = 0L;
        long offlineActiveNodeCount = 0L;
        long tempTokenExpiringSoonCount = 0L;
        long overloadedNodeCount = 0L;
        long lowDiskNodeCount = 0L;
        long totalMaxConcurrency = 0L;
        long totalRunningTasks = 0L;

        for (JudgeNodeToken node : nodes) {
            increment(statusCounts, StrUtil.blankToDefault(node.getStatus(), "unknown"));
            increment(nodeTypeCounts, StrUtil.blankToDefault(node.getNodeType(), "unknown"));
            boolean active = JudgeNodeConstant.TOKEN_ACTIVE.equals(node.getStatus());
            boolean online = active && isOnline(node, now, heartbeatTimeoutSeconds);
            if (active) {
                activeNodeCount++;
            }
            if (online) {
                onlineNodeCount++;
                splitSupportedJudgeModes(node.getSupportedJudgeModes())
                        .forEach(mode -> increment(judgeModeOnlineCounts, mode));
                totalMaxConcurrency += nonNegative(node.getMaxConcurrency());
                totalRunningTasks += nonNegative(node.getRunningTasks());
            } else if (active) {
                offlineActiveNodeCount++;
            }
            if (isTempTokenExpiringSoon(node, now, tempTokenExpireWarnSeconds)) {
                tempTokenExpiringSoonCount++;
            }
            if (online && isOverloaded(node)) {
                overloadedNodeCount++;
            }
            if (online && isLowDisk(node, diskFreeWarnRatio)) {
                lowDiskNodeCount++;
            }
        }

        vo.setTotalNodeCount((long) nodes.size());
        vo.setActiveNodeCount(activeNodeCount);
        vo.setOnlineNodeCount(onlineNodeCount);
        vo.setOfflineActiveNodeCount(offlineActiveNodeCount);
        vo.setFormalNodeCount(nodeTypeCounts.get(JudgeNodeConstant.NODE_TYPE_FORMAL));
        vo.setTempNodeCount(nodeTypeCounts.get(JudgeNodeConstant.NODE_TYPE_TEMP));
        vo.setRevokedNodeCount(statusCounts.get(JudgeNodeConstant.TOKEN_REVOKED));
        vo.setExpiredNodeCount(statusCounts.get(JudgeNodeConstant.TOKEN_EXPIRED));
        vo.setTempTokenExpiringSoonCount(tempTokenExpiringSoonCount);
        vo.setOverloadedNodeCount(overloadedNodeCount);
        vo.setLowDiskNodeCount(lowDiskNodeCount);
        vo.setTotalMaxConcurrency(totalMaxConcurrency);
        vo.setTotalRunningTasks(totalRunningTasks);
        vo.setCapacityUsageRatio(calculateRatio(totalRunningTasks, totalMaxConcurrency));
        vo.setStatusCounts(statusCounts);
        vo.setNodeTypeCounts(nodeTypeCounts);
        vo.setJudgeModeOnlineCounts(judgeModeOnlineCounts);
    }

    private List<String> buildWarnings(JudgeNodeOpsSummaryVo vo) {
        List<String> warnings = new ArrayList<>();
        if (vo.getOnlineNodeCount() == 0) {
            warnings.add("no_online_judge_node");
        }
        if (vo.getOfflineActiveNodeCount() > 0) {
            warnings.add("offline_active_judge_node");
        }
        if (!vo.getJudgeModeOnlineCounts().containsKey(DEFAULT_JUDGE_MODE)) {
            warnings.add("no_default_mode_judge_node");
        }
        if (vo.getOverloadedNodeCount() > 0) {
            warnings.add("overloaded_judge_node");
        }
        if (vo.getLowDiskNodeCount() > 0) {
            warnings.add("low_disk_judge_node");
        }
        if (vo.getTempTokenExpiringSoonCount() > 0) {
            warnings.add("temp_token_expiring");
        }
        return warnings;
    }

    private boolean isOnline(JudgeNodeToken node, LocalDateTime now, long heartbeatTimeoutSeconds) {
        return node.getLastHeartbeatTime() != null
                && node.getLastHeartbeatTime().isAfter(now.minusSeconds(heartbeatTimeoutSeconds));
    }

    private boolean isTempTokenExpiringSoon(JudgeNodeToken node, LocalDateTime now, long warnSeconds) {
        return JudgeNodeConstant.NODE_TYPE_TEMP.equals(node.getNodeType())
                && JudgeNodeConstant.TOKEN_ACTIVE.equals(node.getStatus())
                && node.getExpireTime() != null
                && node.getExpireTime().isAfter(now)
                && !node.getExpireTime().isAfter(now.plusSeconds(warnSeconds));
    }

    private boolean isOverloaded(JudgeNodeToken node) {
        long maxConcurrency = nonNegative(node.getMaxConcurrency());
        return maxConcurrency > 0 && nonNegative(node.getRunningTasks()) >= maxConcurrency;
    }

    private boolean isLowDisk(JudgeNodeToken node, double diskFreeWarnRatio) {
        long totalBytes = nonNegative(node.getDiskTotalBytes());
        long freeBytes = nonNegative(node.getDiskFreeBytes());
        return totalBytes > 0 && BigDecimal.valueOf(freeBytes)
                .divide(BigDecimal.valueOf(totalBytes), 4, RoundingMode.HALF_UP)
                .doubleValue() <= diskFreeWarnRatio;
    }

    private List<String> splitSupportedJudgeModes(String supportedJudgeModes) {
        String normalizedModes = StrUtil.blankToDefault(supportedJudgeModes, DEFAULT_JUDGE_MODE);
        return Arrays.stream(normalizedModes.split(","))
                .map(StrUtil::trimToNull)
                .filter(item -> item != null)
                .distinct()
                .toList();
    }

    private Map<String, Long> initCountMap(List<String> keys) {
        Map<String, Long> counts = new LinkedHashMap<>();
        keys.forEach(key -> counts.put(key, 0L));
        return counts;
    }

    private void increment(Map<String, Long> counts, String key) {
        counts.merge(key, 1L, Long::sum);
    }

    private long nonNegative(Number value) {
        if (value == null) {
            return 0L;
        }
        return Math.max(0L, value.longValue());
    }

    private long positiveOrDefault(long value, long defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private double positiveRatioOrDefault(double value, double defaultValue) {
        return value > 0D && value < 1D ? value : defaultValue;
    }

    private Double calculateRatio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
