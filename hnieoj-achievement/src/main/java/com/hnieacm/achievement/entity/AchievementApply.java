package com.hnieacm.achievement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 成就认证申请实体
 */
@Data
@TableName("achievement_apply")
public class AchievementApply {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uid;

    private String title;

    private String description;

    private String fileUrl;

    private String status;

    private String reason;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}

