package com.hnieacm.submission.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/11
 * @Description: 提交记录列表查询请求
 */
@Data
public class SubmissionListQueryRequest {

    @Min(value = 1, message = "page 必须大于等于 1")
    private Integer page;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Integer pageSize;

    private String problemCode;

    private String language;

    private Integer status;

    private Long contestId;

    private String uid;
}
