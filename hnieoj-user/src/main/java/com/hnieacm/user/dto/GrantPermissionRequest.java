package com.hnieacm.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 批量赋予权限请求
 */
@Data
public class GrantPermissionRequest {

    @NotEmpty(message = "uids 不能为空")
    private List<String> uids;

    @NotBlank(message = "role 不能为空")
    private String role;
}

