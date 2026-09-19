package com.hnieacm.judge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 密钥轮换 prepare 请求。
 *
 * @author Codex
 */
@Data
public class NodeKeyRotationPrepareRequest {

    @NotBlank(message = "rotationId 不能为空")
    @Size(max = 64, message = "rotationId 过长")
    private String rotationId;

    @NotBlank(message = "newPublicKey 不能为空")
    @Size(max = 128, message = "newPublicKey 过长")
    private String newPublicKey;

    @NotBlank(message = "newKeyProof 不能为空")
    @Size(max = 256, message = "newKeyProof 过长")
    private String newKeyProof;
}
