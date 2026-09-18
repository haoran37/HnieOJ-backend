package com.hnieacm.judge.dto;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 内部接口：校验节点对某判题任务测试数据的下载资格
 */
@Data
public class JudgeTaskAccessRequest {

    private String submissionId;

    private String judgeTaskId;

    private String attemptId;

    private Long problemId;
}
