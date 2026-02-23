package com.hnieacm.discussion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论评论实体（discussion_comment）
 */
@Data
@TableName("discussion_comment")
public class DiscussionComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long aid;

    private String content;

    private String uid;

    private String author;

    private String replyToUid;

    private String replyToName;

    private LocalDateTime gmtCreate;
}
