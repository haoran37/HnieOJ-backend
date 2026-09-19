package com.hnieacm.judge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 密钥轮换 confirm 请求。
 *
 * @author Codex
 */
@Data
public class NodeKeyRotationConfirmRequest {

    @NotBlank(message = "signature 不能为空")
    @Size(max = 256, message = "signature 过长")
    private String signature;
}
