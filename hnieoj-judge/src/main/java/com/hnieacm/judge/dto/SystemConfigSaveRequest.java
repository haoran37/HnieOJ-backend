package com.hnieacm.judge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 保存系统配置请求参数
 */
@Data
public class SystemConfigSaveRequest {

    @NotBlank(message = "websiteName 不能为空")
    @Size(max = 255, message = "websiteName 长度不能超过 255")
    private String websiteName;

    @Size(max = 500, message = "logoUrl 长度不能超过 500")
    private String logoUrl;

    @Size(max = 100, message = "icpCode 长度不能超过 100")
    private String icpCode;

    @NotNull(message = "allowRegister 不能为空")
    private Boolean allowRegister;

    @NotBlank(message = "registerMode 不能为空")
    @Size(max = 50, message = "registerMode 长度不能超过 50")
    private String registerMode;

    private List<@Size(max = 100, message = "邮箱后缀长度不能超过 100") String> allowedEmailSuffixes;

    @Size(max = 100, message = "smtpHost 长度不能超过 100")
    private String smtpHost;

    @Min(value = 1, message = "smtpPort 必须大于 0")
    @Max(value = 65535, message = "smtpPort 不能超过 65535")
    private Integer smtpPort;

    @Size(max = 100, message = "smtpEmail 长度不能超过 100")
    private String smtpEmail;

    @Size(max = 255, message = "smtpPassword 长度不能超过 255")
    private String smtpPassword;

    @Size(max = 100, message = "smtpNickname 长度不能超过 100")
    private String smtpNickname;

    @NotNull(message = "submissionInterval 不能为空")
    @Min(value = 1, message = "submissionInterval 最小为 1")
    private Integer submissionInterval;
}
