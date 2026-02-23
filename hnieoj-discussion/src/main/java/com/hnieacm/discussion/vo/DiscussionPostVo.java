package com.hnieacm.discussion.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论主贴展示对象
 */
@Data
public class DiscussionPostVo {

    private Long id;

    private String title;

    private String content;

    private String description;

    private String uid;

    private String author;

    private String role;

    private String category;

    private String problemCode;

    private Integer viewNum;

    private Integer likeNum;

    private Integer topPriority;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
