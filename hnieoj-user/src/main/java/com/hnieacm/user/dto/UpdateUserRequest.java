package com.hnieacm.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
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
     * 状态（兼容 normal/disabled）
     */
    private String status;

    private String phone;

    private String avatar;

    private Long collegeId;

    private Long classId;

    private String college;

    private String grade;

    @JsonProperty("class")
    private String className;
}

