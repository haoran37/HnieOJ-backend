package com.hnieacm.judge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 提交节点注册证明。
 *
 * @author Codex
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NodeEnrollRequest extends NodeEnrollmentChallengeRequest {

    @NotBlank(message = "challengeId 不能为空")
    @Size(max = 64, message = "challengeId 过长")
    private String challengeId;

    @NotBlank(message = "signature 不能为空")
    @Size(max = 256, message = "signature 过长")
    private String signature;
}
