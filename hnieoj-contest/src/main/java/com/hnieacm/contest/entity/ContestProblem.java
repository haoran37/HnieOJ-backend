package com.hnieacm.contest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 竞赛题目关联实体（contest_problem）
 */
@Data
@TableName("contest_problem")
public class ContestProblem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long cid;

    private Long problemId;

    private String displayId;

    private String displayTitle;

    private String color;
}
