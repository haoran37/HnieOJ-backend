package com.hnieacm.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/12
 * @Description: 注册请求参数
 */
@Data
public class RegisterRequest {
    
    @NotBlank(message = "uid不能为空")
    private String uid;
    
    @NotBlank(message = "用户名不能为空")
    private String username;
    
    @NotBlank(message = "密码不能为空")
    private String password;
    
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    
    @NotNull(message = "学院ID不能为空")
    private Long collegeId;
    
    @NotNull(message = "班级ID不能为空")
    private Long classId;
    
    @NotBlank(message = "年级不能为空")
    private String grade;
    
    @NotBlank(message = "QQ不能为空")
    @Pattern(regexp = "\\d{5,11}", message = "QQ号格式不正确")
    private String qq;
}
