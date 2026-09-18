package com.hnieacm.judge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点临时 Token 审计记录
 */
@Data
@TableName("judge_node_token")
public class JudgeNodeToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tokenId;

    private String nodeId;

    private String nodeName;

    private String nodeType;

    private String status;

    private Long authCodeId;

    private LocalDateTime expireTime;

    private LocalDateTime lastUsedTime;

    private LocalDateTime lastHeartbeatTime;

    private Integer maxConcurrency;

    private Long runningTasks;

    private Integer cpuCore;

    private String version;

    private String supportedJudgeModes;

    private Long cacheUsedBytes;

    private Integer cacheProblemCount;

    private Long diskTotalBytes;

    private Long diskFreeBytes;

    private LocalDateTime revokedTime;

    private String revokedBy;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
