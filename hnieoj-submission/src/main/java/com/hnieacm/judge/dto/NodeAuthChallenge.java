package com.hnieacm.judge.dto;

import lombok.Data;

/**
 * Redis 中保存的 WebSocket 认证挑战绑定（JSON 序列化）。
 *
 * @author Codex
 */
@Data
public class NodeAuthChallenge {

    private String challengeId;

    /**
     * 物理挑战绑定的请求 ID：AUTH_RESPONSE 必须回传同一 requestId，
     * 防止把某次挑战的签名挪用到另一个请求/连接上下文。
     */
    private String requestId;

    private String connectionId;

    private String nonce;

    private String audience;

    private long expiresAt;

    /** 由 AUTH_REFRESH 发起的挑战为 true，成功后保持 sessionEpoch 不变。 */
    private boolean refresh;

    /**
     * 刷新挑战绑定的节点 ID；首次连接挑战为 null。
     *
     * <p>用于阻止旧连接刷新时换绑其他节点，或读取最新纪元伪装当前会话。</p>
     */
    private String nodeId;

    /** 刷新挑战绑定的会话纪元；首次连接挑战为 null。 */
    private Long sessionEpoch;
}
