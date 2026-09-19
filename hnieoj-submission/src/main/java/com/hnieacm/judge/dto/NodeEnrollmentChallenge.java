package com.hnieacm.judge.dto;

import lombok.Data;

/**
 * Redis 中保存的注册挑战绑定（JSON 序列化）。
 *
 * @author Codex
 */
@Data
public class NodeEnrollmentChallenge {

    private Long authCodeId;

    private String bootstrapDigest;

    private String enrollmentId;

    private String nodeName;

    private String publicKey;

    private String nonce;

    private String audience;

    private long expiresAt;

    /** 非空表示这是已成功注册后的恢复挑战，直接返回该节点身份。 */
    private String recoveryNodeId;
}
