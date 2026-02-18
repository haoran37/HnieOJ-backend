package com.hnieacm.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/17
 * @Description: 班级助教关联实体（sys_class_ta）
 */
@Data
@TableName("sys_class_ta")
public class SysClassTa {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long classId;

    private String taUid;

    private LocalDateTime gmtCreate;
}
