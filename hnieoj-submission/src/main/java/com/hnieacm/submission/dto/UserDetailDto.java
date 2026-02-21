package com.hnieacm.submission.dto;

import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 用户详细信息 DTO（来自用户服务公共 API）
 */
@Data
public class UserDetailDto {

    private String uid;

    private String username;

    private String realname;

    private List<String> roles;
}

