package com.hnieacm.judge.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 测试邮件 SMTP 配置请求参数
 */
@Data
public class TestEmailSmtpConfigRequest {

    @NotBlank(message = "smtpHost 不能为空")
    @Size(max = 100, message = "smtpHost 长度不能超过 100")
    private String smtpHost;

    @NotNull(message = "smtpPort 不能为空")
    @Min(value = 1, message = "smtpPort 必须大于 0")
    @Max(value = 65535, message = "smtpPort 不能超过 65535")
    private Integer smtpPort;

    @NotBlank(message = "smtpEmail 不能为空")
    @Email(message = "smtpEmail 邮箱格式不正确")
    @Size(max = 100, message = "smtpEmail 长度不能超过 100")
    private String smtpEmail;

    @NotBlank(message = "smtpPassword 不能为空")
    @Size(max = 255, message = "smtpPassword 长度不能超过 255")
    private String smtpPassword;

    @Size(max = 100, message = "smtpNickname 长度不能超过 100")
    private String smtpNickname;
}
