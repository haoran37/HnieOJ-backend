package com.hnieacm.judge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 判题节点注册事实记录。
 *
 * <p>历史表名保留；{@code tokenId} 为稳定 registryID，不再是短期 JWT jti。
 * {@code authorizationUntil}/{@code expireTime} 为节点硬截止，与短期 accessToken exp 不同。
 * {@code sessionEpoch}/{@code accessVersion}/{@code activeKeyId} 由数据库权威维护。</p>
 *
 * @author Codex
 */
@Data
@TableName("judge_node_token")
public class JudgeNodeToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 稳定 registryID（原 jti 语义已退休）。 */
    private String tokenId;

    private String nodeId;

    private String nodeName;

    private String nodeType;

    private String status;

    private Long authCodeId;

    private String instanceId;

    private String fingerprintHash;

    private String boundSourceIp;

    private String proofType;

    private String publicKey;

    private String publicKeyHash;

    private LocalDateTime expireTime;

    private LocalDateTime lastUsedTime;

    private LocalDateTime lastHeartbeatTime;

    private LocalDateTime lastSeenAt;

    private String lastSeenIp;

    private Integer maxConcurrency;

    private Long runningTasks;

    private Integer cpuCore;

    private String version;

    private String supportedJudgeModes;

    private String diagnosticMessage;

    private Long cacheUsedBytes;

    private Integer cacheProblemCount;

    private Long diskTotalBytes;

    private Long diskFreeBytes;

    private LocalDateTime revokedTime;

    private String revokedBy;

    /** 会话纪元，新会话接管时原子自增；旧连接业务写入据此被拒绝。 */
    private Long sessionEpoch;

    /** 访问版本，轮换/吊销时自增。 */
    private Integer accessVersion;

    /** 当前激活密钥 ID。 */
    private String activeKeyId;

    private Integer weight;

    private Boolean draining;

    private LocalDateTime authorizationUntil;

    private String enrollmentId;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
