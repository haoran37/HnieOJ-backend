package com.hnieacm.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 创建用户请求
 */
@Data
public class CreateUserRequest {

    @NotBlank(message = "uid 不能为空")
    private String uid;

    @NotBlank(message = "username 不能为空")
    private String username;

    @Email(message = "email 格式不正确")
    private String email;

    /**
     * 管理员可选传入密码；不传则使用系统默认初始密码。
     */
    private String password;

    private String phone;

    private String avatar;

    private Long collegeId;

    private Long classId;

    private String grade;
}
