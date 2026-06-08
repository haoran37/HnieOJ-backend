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
 * @Description: 判题任务 outbox 实体
 */
@Data
@TableName("judge_task_outbox")
public class JudgeTaskOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("message_id")
    private String messageId;

    @TableField("judge_task_id")
    private String judgeTaskId;

    @TableField("submission_id")
    private String submissionId;

    @TableField("exchange_name")
    private String exchangeName;

    @TableField("routing_key")
    private String routingKey;

    private String payload;

    private String status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("publish_attempt")
    private Integer publishAttempt;

    @TableField("max_retry_count")
    private Integer maxRetryCount;

    @TableField("next_retry_time")
    private LocalDateTime nextRetryTime;

    @TableField("sent_time")
    private LocalDateTime sentTime;

    @TableField("last_error")
    private String lastError;

    @TableField("gmt_create")
    private LocalDateTime gmtCreate;

    @TableField("gmt_modified")
    private LocalDateTime gmtModified;
}
