package com.hnieacm.submission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: Rejudge task detail.
 */
@Data
@TableName("rejudge_task_detail")
public class RejudgeTaskDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("judge_id")
    private Long judgeId;

    @TableField("submit_id")
    private String submitId;

    @TableField("problem_id")
    private Long problemId;

    @TableField("problem_code")
    private String problemCode;

    private String uid;

    private String username;

    private String language;

    @TableField("original_status")
    private Integer originalStatus;

    @TableField("original_score")
    private Integer originalScore;

    @TableField("original_time")
    private Integer originalTime;

    @TableField("original_memory")
    private Integer originalMemory;

    @TableField("judge_task_id")
    private String judgeTaskId;

    @TableField("final_status")
    private Integer finalStatus;

    @TableField("final_score")
    private Integer finalScore;

    @TableField("final_time")
    private Integer finalTime;

    @TableField("final_memory")
    private Integer finalMemory;

    @TableField("finished_time")
    private LocalDateTime finishedTime;

    @TableField("submit_time")
    private LocalDateTime submitTime;

    @TableField("gmt_create")
    private LocalDateTime gmtCreate;

    @TableField("gmt_modified")
    private LocalDateTime gmtModified;
}
