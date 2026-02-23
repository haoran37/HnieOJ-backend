package com.hnieacm.discussion.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论评论展示对象
 */
@Data
public class DiscussionCommentVo {

    private Long id;

    private String content;

    private String uid;

    private String author;

    private String replyToUid;

    private String replyToName;

    private LocalDateTime gmtCreate;
}
