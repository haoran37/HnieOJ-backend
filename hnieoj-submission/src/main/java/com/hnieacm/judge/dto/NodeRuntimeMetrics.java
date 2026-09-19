package com.hnieacm.judge.dto;

import lombok.Data;

/**
 * WSS HEARTBEAT 运行指标（只写运行指标列，绝不覆盖配额/模式/状态）。
 *
 * @author Codex
 */
@Data
public class NodeRuntimeMetrics {

    private Integer cpuCore;

    private String version;

    private Long runningTasks;

    private Long cacheUsedBytes;

    private Integer cacheProblemCount;

    private Long diskTotalBytes;

    private Long diskFreeBytes;

    /** 是否存在任一需要落库的指标。 */
    public boolean hasAny() {
        return cpuCore != null || version != null || runningTasks != null || cacheUsedBytes != null
                || cacheProblemCount != null || diskTotalBytes != null || diskFreeBytes != null;
    }
}
