package com.hnieacm.discussion.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 新建讨论请求
 */
@Data
public class CreateDiscussionRequest {

    @NotBlank(message = "title 不能为空")
    private String title;

    @NotBlank(message = "category 不能为空")
    private String category;

    private String problemCode;

    private Boolean isTop;

    @NotBlank(message = "content 不能为空")
    private String content;
}
