package com.hnieacm.discussion.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 管理端讨论列表展示对象
 */
@Data
public class AdminDiscussionListVo {

    private Long id;

    private String title;

    private String uid;

    private String author;

    private String category;

    private String problemCode;

    private Integer status;

    private Integer topPriority;

    private Integer viewNum;

    private Integer likeNum;

    private Integer answerCount;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
