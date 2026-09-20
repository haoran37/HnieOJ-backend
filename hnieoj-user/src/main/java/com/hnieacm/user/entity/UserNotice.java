package com.hnieacm.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 管理员定向通知实体（user_notice）
 */
@Data
@TableName("user_notice")
public class UserNotice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    /**
     * USERS / CLASSES
     */
    private String targetType;

    /**
     * 目标 ID 的 JSON 字符串数组（USERS 为 uid，CLASSES 为班级 id）。
     */
    private String targetSpec;

    /**
     * DRAFT / PUBLISHED
     */
    private String status;

    private String creatorUid;

    private LocalDateTime publishedAt;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
