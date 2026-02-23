package com.hnieacm.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 题单分类实体（training_category）
 */
@Data
@TableName("training_category")
public class TrainingCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String color;
}
