package com.hnieacm.common.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/06
 * @Description: 判题任务消息
 */
@Data
public class JudgeTaskMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String messageId;

    private Long judgeId;

    private String submissionId;

    private Long problemId;

    private String problemCode;

    private String uid;

    private String language;

    private Long createdAtMillis;
}
