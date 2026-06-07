package com.hnieacm.submission.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 重判任务查询请求
 */
@Data
public class RejudgeTaskQueryRequest {

    @Min(value = 1, message = "page 必须大于等于 1")
    private Integer page;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Integer pageSize;

    private String status;

    private String problemCode;
}
