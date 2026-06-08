package com.hnieacm.user.dto;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户资料修改申请请求
 */
@Data
public class UserProfileChangeApplyRequest {

    private String username;

    private String email;

    private String phone;

    private String avatar;

    private Long collegeId;

    private Long classId;

    private String grade;

    private String realname;

    private String qq;

    private String cfUsername;

    private String github;

    private String blog;
}
