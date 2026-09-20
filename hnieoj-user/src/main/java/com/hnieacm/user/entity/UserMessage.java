package com.hnieacm.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 站内消息实体（user_message），发布通知时按收件人快照生成
 */
@Data
@TableName("user_message")
public class UserMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noticeId;

    private String recipientUid;

    /**
     * 发布时的标题快照，通知后续不可编辑，此处不复引用通知标题。
     */
    private String title;

    /**
     * 发布时的正文快照。
     */
    private String content;

    private LocalDateTime readAt;

    /**
     * 软删除标记，删除后不再出现在本人收件箱与未读数中。
     */
    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;
}
