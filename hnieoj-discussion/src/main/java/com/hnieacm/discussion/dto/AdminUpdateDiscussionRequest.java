package com.hnieacm.discussion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 管理端编辑讨论请求
 */
@Data
public class AdminUpdateDiscussionRequest {

    @NotBlank(message = "title 不能为空")
    private String title;

    @NotBlank(message = "category 不能为空")
    private String category;

    private String problemCode;

    @NotBlank(message = "content 不能为空")
    private String content;

    @NotNull(message = "status 不能为空")
    private Integer status;

    @NotNull(message = "isTop 不能为空")
    private Boolean isTop;
}
