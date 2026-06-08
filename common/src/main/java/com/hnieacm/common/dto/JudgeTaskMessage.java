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

    private Integer schemaVersion;

    private String judgeTaskId;

    private Long judgeId;

    private String submissionId;

    private Long problemId;

    private String problemCode;

    private String uid;

    private String language;

    private String code;

    private Integer timeLimit;

    private Integer memoryLimit;

    private Integer stackLimit;

    private String judgeMode;

    private JudgeAsset checker;

    private JudgeAsset interactor;

    private InteractionConfig interaction;

    private Integer problemType;

    private Integer ioScore;

    private Boolean isRemoveEndBlank;

    private Integer dataVersion;

    private Long contestId;

    private String createdAt;

    private Long createdAtMillis;

    @Data
    public static class JudgeAsset implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String language;

        private String source;

        private String artifactFileId;

        private Integer timeLimit;

        private Integer memoryLimit;

        private Integer stackLimit;

        private Integer outputLimit;

        private String protocol;

        private String argumentTemplate;
    }

    @Data
    public static class InteractionConfig implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String protocol;

        private String wiring;

        private String scoreMode;
    }
}
