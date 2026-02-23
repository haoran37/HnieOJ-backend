package com.hnieacm.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 题单实体（training）
 */
@Data
@TableName("training")
public class Training {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    private String author;

    private String type;

    private String auth;

    private String privatePwd;

    private Integer status;

    @TableField("`rank`")
    private Integer rank;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
