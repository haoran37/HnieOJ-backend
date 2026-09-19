package com.hnieacm.submission.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/19
 * @Description: 节点重连后的 RESUME_RESULT：区分已恢复与已拒绝 attempt
 */
@Data
public class JudgeTaskResumeVo {

    private List<Resumed> resumed = new ArrayList<>();

    private List<Rejected> rejected = new ArrayList<>();

    @Data
    public static class Resumed {

        private String submissionId;

        private String judgeTaskId;

        private String attemptId;

        private Long leaseUntil;

        private Integer renewAfterMillis;

        /**
         * 该 attempt 已产出终态结果时置 true；节点应按结果重放对账，而不是重跑。
         */
        private boolean completed;
    }

    @Data
    public static class Rejected {

        private String submissionId;

        private String judgeTaskId;

        private String attemptId;

        private String reason;
    }
}
