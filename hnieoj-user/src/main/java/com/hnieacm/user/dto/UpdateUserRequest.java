package com.hnieacm.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 更新用户信息请求
 */
@Data
public class UpdateUserRequest {

    private String username;

    @Email(message = "email 格式不正确")
    private String email;

    /**
     * 状态（0：正常，1：禁用）
     */
    @Min(value = 0, message = "status 只能为0或1")
    @Max(value = 1, message = "status 只能为0或1")
    private Integer status;

    private String phone;

    private String avatar;

    private Long collegeId;

    private Long classId;

    private String grade;
}
