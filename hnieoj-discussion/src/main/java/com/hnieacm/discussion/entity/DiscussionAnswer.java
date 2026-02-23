package com.hnieacm.discussion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论回答实体（discussion_answer）
 */
@Data
@TableName("discussion_answer")
public class DiscussionAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long did;

    private String content;

    private String uid;

    private String author;

    private Integer likeNum;

    private Integer status;

    private LocalDateTime gmtCreate;
}
