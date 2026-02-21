package com.hnieacm.submission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 提交代码请求
 */
@Data
public class SubmitCodeRequest {

    @NotBlank(message = "problemCode不能为空")
    private String problemCode;

    @NotBlank(message = "language不能为空")
    private String language;

    /**
     * 代码内容（与 file 二选一）
     */
    private String code;

    /**
     * 比赛ID（可选）
     */
    private String contestId;
}
