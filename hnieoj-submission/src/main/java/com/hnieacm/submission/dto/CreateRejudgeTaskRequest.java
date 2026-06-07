package com.hnieacm.submission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 创建重判任务请求
 */
@Data
public class CreateRejudgeTaskRequest {

    @NotBlank(message = "problemCode 不能为空")
    private String problemCode;

    private Long contestId;

    private LocalDateTime rangeStart;

    private LocalDateTime rangeEnd;
}
