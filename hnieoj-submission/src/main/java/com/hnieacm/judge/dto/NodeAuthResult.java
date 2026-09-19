package com.hnieacm.judge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * WSS 认证成功后的短期身份与配额快照。
 *
 * @author Codex
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeAuthResult {

    private String nodeId;

    private String keyId;

    private String accessToken;

    private long accessExpiresAt;

    private long sessionEpoch;

    private long serverTime;

    private Integer maxConcurrency;

    private List<String> supportedJudgeModes;

    private Long authorizationUntil;

    private long heartbeatIntervalMillis;

    /**
     * 令牌绑定的访问版本；连接后续业务操作据此拒绝已被吊销/降权的旧令牌。
     * 不进入 AUTH_OK 线上载荷，仅服务端内部使用。
     */
    private Integer accessVersion;

    /**
     * 调度权重（DB 权威策略快照）；Dispatcher 据此做有界加权公平调度，
     * 低权重节点保底 1 个槽位，不进入 AUTH_OK 线上载荷。
     */
    private Integer weight;

    /**
     * 认证时刻的权威节点状态（active/draining）；用于认证成功后通过 NODE_STATE
     * 让重连/刷新连接立即观察到排空状态，不进入 AUTH_OK 线上载荷。
     */
    private String status;

    /** 认证时刻的权威排空标记，与 {@link #status} 配合。 */
    private Boolean draining;
}
