package com.hnieacm.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 本人站内消息展示对象。不回显收件人集合/通知目标。
 */
@Data
public class UserMessageVo {

    private Long id;

    private Long noticeId;

    private String title;

    private String content;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
