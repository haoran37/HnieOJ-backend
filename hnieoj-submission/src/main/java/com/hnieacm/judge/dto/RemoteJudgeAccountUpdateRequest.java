package com.hnieacm.judge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 更新远程评测账号请求；字段缺省表示保留原值，密码缺失或空白保留，非空密码不 trim。
 */
@Data
public class RemoteJudgeAccountUpdateRequest {

    @Size(max = 20, message = "oj 长度不能超过 20")
    private String oj;

    @Size(max = 100, message = "username 长度不能超过 100")
    private String username;

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
