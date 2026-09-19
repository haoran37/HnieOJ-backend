package com.hnieacm.judge.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册挑战响应。
 *
 * @author Codex
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeEnrollmentChallengeVo {

    private String challengeId;

    private String nonce;

    private String audience;

    private long expiresAt;

    private long serverTime;
}
