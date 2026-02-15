package com.hnieacm.user.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 权限用户列表展示对象
 */
@Data
public class PermissionUserVo {

    private String uid;

    private String username;

    private String college;

    private String majorClass;

    private String email;

    private String phone;

    private List<String> roles;
}
