package com.hnieacm.submission.dto;

import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/19
 * @Description: 节点重连后的 RESUME_TASKS 请求
 */
@Data
public class JudgeTaskResumeRequest {

    private List<Attempt> attempts;

    @Data
    public static class Attempt {

        private String submissionId;

        private String judgeTaskId;

        private String attemptId;
    }
}
