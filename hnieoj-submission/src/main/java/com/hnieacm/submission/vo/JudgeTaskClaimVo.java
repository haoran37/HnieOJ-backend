package com.hnieacm.submission.vo;

import com.hnieacm.common.dto.JudgeTaskMessage;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 判题任务领取响应
 */
@Data
public class JudgeTaskClaimVo {

    private JudgeTaskMessage task;

    private String attemptId;

    private Long leaseUntil;

    private Integer renewAfterMillis;
}
