package com.hnieacm.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 身份资料变更申请展示对象
 */
@Data
public class ProfileChangeVo {

    private Long id;

    private String uid;

    private ProfileIdentityVo original;

    private ProfileIdentityVo proposed;

    private String reason;

    private String status;

    private String reviewerUid;

    private String reviewReason;

    private LocalDateTime reviewAt;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
