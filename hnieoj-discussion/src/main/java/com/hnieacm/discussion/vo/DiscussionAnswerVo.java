package com.hnieacm.discussion.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论回答展示对象
 */
@Data
public class DiscussionAnswerVo {

    private Long id;

    private Long did;

    private String content;

    private String uid;

    private String author;

    private Integer likeNum;

    private LocalDateTime gmtCreate;

    private List<DiscussionCommentVo> comments;
}
