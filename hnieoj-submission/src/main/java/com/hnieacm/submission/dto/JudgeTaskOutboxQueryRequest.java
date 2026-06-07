package com.hnieacm.submission.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题任务 outbox 查询请求
 */
@Data
public class JudgeTaskOutboxQueryRequest {

    @Min(value = 1, message = "page 必须大于等于 1")
    private Integer page;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Integer pageSize;

    private String status;

    private String submissionId;

    private String judgeTaskId;
}
