package com.hnieacm.judge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务端生成的 WebSocket 认证挑战（下发给节点）。
 *
 * @author Codex
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeAuthChallengeVo {

    private String challengeId;

    private String nonce;

    private String audience;

    private long expiresAt;

    private long serverTime;

    /** 关联的请求 ID；初始建连时由服务端生成，刷新时沿用 AUTH_REFRESH 的 requestId。 */
    private String requestId;
}
