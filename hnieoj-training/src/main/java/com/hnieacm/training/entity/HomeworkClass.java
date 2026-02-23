package com.hnieacm.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 作业班级关联实体（homework_class）
 */
@Data
@TableName("homework_class")
public class HomeworkClass {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long hid;

    private Long classId;
}
