package com.hnieacm.judge.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点 Token 审计展示
 */
@Data
public class JudgeNodeTokenVo {

    private Long id;

    private String tokenId;

    private String nodeId;

    private String nodeName;

    private String nodeType;

    private String status;

    private LocalDateTime expireTime;

    private LocalDateTime lastUsedTime;

    private LocalDateTime lastHeartbeatTime;

    private Boolean online;

    private Integer maxConcurrency;

    private Long runningTasks;

    private Integer cpuCore;

    private String version;

    private List<String> supportedJudgeModes;

    private Long cacheUsedBytes;

    private Integer cacheProblemCount;

    private Long diskTotalBytes;

    private Long diskFreeBytes;

    private LocalDateTime gmtCreate;
}
