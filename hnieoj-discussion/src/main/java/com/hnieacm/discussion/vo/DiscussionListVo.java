package com.hnieacm.discussion.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论列表展示对象
 */
@Data
public class DiscussionListVo {

    private Long id;

    private String title;

    private String description;

    private String uid;

    private String author;

    private String role;

    private String category;

    private String problemCode;

    private Integer viewNum;

    private Integer likeNum;

    private Integer topPriority;

    private Integer answerCount;

    private LocalDateTime gmtCreate;
}
