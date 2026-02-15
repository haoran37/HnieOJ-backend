package com.hnieacm.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 修改用户密码请求
 */
@Data
public class UpdateUserPasswordRequest {

    @NotBlank(message = "password 不能为空")
    private String password;
}

