package com.hnieacm.judge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 申请节点注册挑战。
 *
 * @author Codex
 */
@Data
public class NodeEnrollmentChallengeRequest {

    @NotBlank(message = "bootstrapToken 不能为空")
    @Size(max = 256, message = "bootstrapToken 过长")
    private String bootstrapToken;

    @NotBlank(message = "enrollmentId 不能为空")
    @Size(max = 64, message = "enrollmentId 过长")
    private String enrollmentId;

    @NotBlank(message = "nodeName 不能为空")
    @Size(max = 100, message = "nodeName 过长")
    private String nodeName;

    @NotBlank(message = "publicKey 不能为空")
    @Size(max = 128, message = "publicKey 过长")
    private String publicKey;
}
