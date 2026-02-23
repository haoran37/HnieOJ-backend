package com.hnieacm.discussion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论点赞记录实体（discussion_like）
 */
@Data
@TableName("discussion_like")
public class DiscussionLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uid;

    private Long targetId;

    private String targetType;

    private String direction;
}
