package com.hnieacm.achievement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 用户成就记录实体
 */
@Data
@TableName("user_achievement")
public class UserAchievement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uid;

    private String title;

    private String content;

    private String proofUrl;

    private LocalDateTime achieveTime;

    private Integer status;

    private LocalDateTime gmtCreate;
}

