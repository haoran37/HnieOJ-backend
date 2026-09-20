package com.hnieacm.judge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 创建远程评测账号请求；密码只写不回显，toString 不输出密码。
 */
@Data
public class RemoteJudgeAccountCreateRequest {

    @NotBlank(message = "oj 不能为空")
    @Size(max = 20, message = "oj 长度不能超过 20")
    private String oj;

    @NotBlank(message = "username 不能为空")
    @Size(max = 100, message = "username 长度不能超过 100")
    private String username;

    @NotBlank(message = "password 不能为空")
    @Size(max = 255, message = "password 长度不能超过 255")
    @ToString.Exclude
    private String password;

    @Min(value = 0, message = "status 只能为 0 或 1")
    @Max(value = 1, message = "status 只能为 0 或 1")
    private Integer status;

    @Min(value = 1, message = "maxConcurrency 必须为 1 到 100")
    @Max(value = 100, message = "maxConcurrency 必须为 1 到 100")
    private Integer maxConcurrency;
}
