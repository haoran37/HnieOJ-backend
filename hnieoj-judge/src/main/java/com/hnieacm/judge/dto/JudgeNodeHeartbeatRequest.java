package com.hnieacm.judge.dto;

import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题节点心跳请求
 */
@Data
public class JudgeNodeHeartbeatRequest {

    private String nodeId;

    private String nodeName;

    private String nodeType;

    private Integer maxConcurrency;

    private Long runningTasks;

    private Integer cpuCore;

    private String version;

    private List<String> supportedJudgeModes;

    private Long cacheUsedBytes;

    private Integer cacheProblemCount;

    private Long diskTotalBytes;

    private Long diskFreeBytes;
}
