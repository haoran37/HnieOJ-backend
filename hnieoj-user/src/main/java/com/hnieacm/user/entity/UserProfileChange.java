package com.hnieacm.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 用户身份资料变更申请实体（user_profile_change）
 */
@Data
@TableName("user_profile_change")
public class UserProfileChange {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uid;

    /**
     * 申请时的原始身份字段 JSON（仅 realname/collegeId/grade/classId）。
     */
    private String original;

    /**
     * 期望变更后的身份字段 JSON（仅 realname/collegeId/grade/classId）。
     */
    private String proposed;

    private String reason;

    /**
     * PENDING / APPROVED / REJECTED
     */
    private String status;

    private String reviewerUid;

    private String reviewReason;

    private LocalDateTime reviewAt;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
