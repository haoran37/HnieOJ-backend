package com.hnieacm.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 题单分类关联实体（training_category_rel）
 */
@Data
@TableName("training_category_rel")
public class TrainingCategoryRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tid;

    private Long cid;
}
