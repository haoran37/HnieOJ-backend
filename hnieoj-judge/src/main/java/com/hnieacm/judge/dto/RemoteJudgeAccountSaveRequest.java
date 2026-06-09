package com.hnieacm.judge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 保存远程评测账号请求参数
 */
@Data
public class RemoteJudgeAccountSaveRequest {

    @NotBlank(message = "oj 不能为空")
    @Size(max = 20, message = "oj 长度不能超过 20")
    private String oj;

    @NotBlank(message = "username 不能为空")
    @Size(max = 100, message = "username 长度不能超过 100")
    private String username;

    @Size(max = 255, message = "password 长度不能超过 255")
    private String password;

    @NotNull(message = "maxConcurrency 不能为空")
    @Min(value = 1, message = "maxConcurrency 最小为 1")
    @Max(value = 100, message = "maxConcurrency 不能超过 100")
    private Integer maxConcurrency;

    @NotNull(message = "status 不能为空")
    @Min(value = 0, message = "status 仅支持 0 或 1")
    @Max(value = 1, message = "status 仅支持 0 或 1")
    private Integer status;
}
