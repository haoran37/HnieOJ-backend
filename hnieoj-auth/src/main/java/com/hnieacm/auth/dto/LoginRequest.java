package com.hnieacm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/12
 * @Description: 登录请求参数
 */
@Data
public class LoginRequest {
    
    @NotBlank(message = "uid不能为空")
    private String uid;
    
    @NotBlank(message = "密码不能为空")
    private String password;
}
