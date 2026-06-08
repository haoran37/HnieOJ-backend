package com.hnieacm.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户资料修改申请实体
 */
@Data
@TableName("user_profile_change_apply")
public class UserProfileChangeApply {

    @TableId(type = IdType.AUTO)
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
