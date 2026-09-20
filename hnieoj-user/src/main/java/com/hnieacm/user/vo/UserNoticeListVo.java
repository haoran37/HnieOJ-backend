package com.hnieacm.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 定向通知列表展示对象
 */
@Data
public class UserNoticeListVo {

    private Long id;

    private String title;

    private String targetType;

    private String status;

    private String creatorUid;

    private LocalDateTime publishedAt;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
