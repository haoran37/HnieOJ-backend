package com.hnieacm.submission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 判题任务执行租约实体，MySQL 是任务所有权与执行资格的权威状态
 */
@Data
@TableName("judge_task_execution")
public class JudgeTaskExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("submission_id")
    private String submissionId;

    @TableField("judge_id")
    private Long judgeId;

    @TableField("judge_task_id")
    private String judgeTaskId;

    @TableField("problem_id")
    private Long problemId;

    @TableField("problem_code")
    private String problemCode;

    @TableField("judge_mode")
    private String judgeMode;

    @TableField("stream_key")
    private String streamKey;

    @TableField("stream_id")
    private String streamId;

    @TableField("node_id")
    private String nodeId;

    @TableField("token_id")
    private String tokenId;

    @TableField("attempt_id")
    private String attemptId;

    @TableField("attempt_count")
    private Integer attemptCount;

    @TableField("max_attempt_count")
    private Integer maxAttemptCount;

    private String status;

    @TableField("lease_until")
    private Long leaseUntil;

    @TableField("renew_after_millis")
    private Integer renewAfterMillis;

    @TableField("execution_deadline")
    private Long executionDeadline;

    @TableField("last_error")
    private String lastError;

    @TableField("terminal_fingerprint")
    private String terminalFingerprint;

    @TableField("gmt_create")
    private LocalDateTime gmtCreate;

    @TableField("gmt_modified")
    private LocalDateTime gmtModified;
}
