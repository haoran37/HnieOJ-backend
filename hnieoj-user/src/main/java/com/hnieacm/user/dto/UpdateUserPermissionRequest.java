package com.hnieacm.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 更新用户权限请求
 */
@Data
public class UpdateUserPermissionRequest {

    @NotBlank(message = "uid 不能为空")
    private String uid;

    @NotBlank(message = "role 不能为空")
    private String role;
}

