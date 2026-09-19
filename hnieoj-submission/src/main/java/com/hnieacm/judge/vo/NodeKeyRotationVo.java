package com.hnieacm.judge.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 密钥轮换状态（prepare / confirm / query 共用，只暴露公开状态）。
 *
 * @author Codex
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeKeyRotationVo {

    private String rotationId;

    private String keyId;

    /** PENDING / ACTIVE / EXPIRED */
    private String status;

    private String newPublicKey;

    private String confirmNonce;

    private Long expiresAt;

    private Long graceUntil;
}
