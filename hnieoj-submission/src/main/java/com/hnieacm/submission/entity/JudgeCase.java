package com.hnieacm.submission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/11
 * @Description: 数据库实体类，对应表：judge_case
 */
@Data
@TableName("judge_case")
public class JudgeCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long submitId;

    private String caseId;

    private Integer status;

    private Integer time;

    private Integer memory;

    private Integer score;

    private String inputData;

    private String outputData;

    private String userOutput;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
