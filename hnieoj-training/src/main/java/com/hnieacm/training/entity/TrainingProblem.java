package com.hnieacm.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 题单题目关联实体（training_problem）
 */
@Data
@TableName("training_problem")
public class TrainingProblem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tid;

    private Long problemId;

    private Integer displayId;
}
