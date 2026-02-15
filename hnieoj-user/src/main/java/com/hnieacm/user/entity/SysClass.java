package com.hnieacm.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 班级实体（sys_class）
 */
@Data
@TableName("sys_class")
public class SysClass {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long collegeId;

    private String grade;

    private String name;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}

