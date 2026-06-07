package com.hnieacm.submission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 重判任务实体
 */
@Data
@TableName("rejudge_task")
public class RejudgeTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("problem_id")
    private Long problemId;

    @TableField("problem_code")
    private String problemCode;

    @TableField("contest_id")
    private Long contestId;

    @TableField("range_start")
    private LocalDateTime rangeStart;

    @TableField("range_end")
    private LocalDateTime rangeEnd;

    private String status;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("processed_count")
    private Integer processedCount;

    @TableField("failed_count")
    private Integer failedCount;

    @TableField("last_judge_id")
    private Long lastJudgeId;

    @TableField("last_error")
    private String lastError;

    @TableField("admin_id")
    private String adminId;

    @TableField("gmt_create")
    private LocalDateTime gmtCreate;

    @TableField("gmt_modified")
    private LocalDateTime gmtModified;
}
