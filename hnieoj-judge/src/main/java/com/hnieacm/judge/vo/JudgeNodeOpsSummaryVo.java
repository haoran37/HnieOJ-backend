package com.hnieacm.judge.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: Judge node operation summary view object.
 */
@Data
public class JudgeNodeOpsSummaryVo {

    private LocalDateTime generatedAt;

    private Long totalNodeCount;

    private Long activeNodeCount;

    private Long onlineNodeCount;

    private Long offlineActiveNodeCount;

    private Long formalNodeCount;

    private Long tempNodeCount;

    private Long revokedNodeCount;

    private Long expiredNodeCount;

    private Long tempTokenExpiringSoonCount;

    private Long overloadedNodeCount;

    private Long lowDiskNodeCount;

    private Long totalMaxConcurrency;

    private Long totalRunningTasks;

    private Double capacityUsageRatio;

    private Map<String, Long> statusCounts;

    private Map<String, Long> nodeTypeCounts;

    private Map<String, Long> judgeModeOnlineCounts;

    private Long heartbeatTimeoutSeconds;

    private Long tempTokenExpireWarnSeconds;

    private Double diskFreeWarnRatio;

    private Boolean healthy;

    private List<String> warnings;
}
