package com.hnieacm.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 数据库实体类，对应表：problem_tag.
 */
@Data
@TableName("problem_tag")
public class ProblemTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * problem.id
     */
    @TableField("problem_id")
    private Long problemId;

    /**
     * tag.id
     */
    private Long tid;
}
