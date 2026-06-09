package com.hnieacm.judge.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 发送测试邮件请求参数
 */
@Data
public class TestEmailRequest {

    @NotBlank(message = "recipient 不能为空")
    @Email(message = "recipient 邮箱格式不正确")
    private String recipient;

    @Valid
    @NotNull(message = "smtpConfig 不能为空")
    private TestEmailSmtpConfigRequest smtpConfig;
}
