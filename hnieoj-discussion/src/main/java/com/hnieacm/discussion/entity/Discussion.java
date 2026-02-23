package com.hnieacm.discussion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论帖子实体（discussion）
 */
@Data
@TableName("discussion")
public class Discussion {

    @TableId(type = IdType.AUTO)
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

    private Integer status;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
