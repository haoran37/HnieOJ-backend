package com.hnieacm.judge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/06
 * @Description: 数据库实体类，对应表：judge
 */
@Data
@TableName("judge")
public class Judge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String submitId;

    @TableField("problem_id")
    private Long problemId;

    @TableField("problem_code")
    private String problemCode;

    private String uid;

    private String language;

    private Integer status;
}
