package com.hnieacm.submission.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: Legacy rejudge task request.
 */
@Data
public class LegacyCreateRejudgeTaskRequest {

    private String problemId;

    private String problemCode;

    private Long contestId;

    private Object range;

    private LocalDateTime rangeStart;

    private LocalDateTime rangeEnd;
}
