package com.hnieacm.submission.dto;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 判题任务续租请求
 */
@Data
public class JudgeTaskLeaseRequest {

    private String judgeTaskId;

    private String attemptId;
}
