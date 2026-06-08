package com.hnieacm.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: 当前用户修改密码请求
 */
@Data
public class ChangeCurrentPasswordRequest {

    @NotBlank(message = "oldPassword 不能为空")
    private String oldPassword;

    @NotBlank(message = "password 不能为空")
    private String password;
}
