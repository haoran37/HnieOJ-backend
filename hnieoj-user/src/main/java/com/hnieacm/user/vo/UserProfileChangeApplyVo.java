package com.hnieacm.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户资料修改申请视图对象
 */
@Data
public class UserProfileChangeApplyVo {

    private Long id;

    private String uid;

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

    private String status;

    private String reason;

    private String reviewerUid;

    private LocalDateTime reviewTime;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
