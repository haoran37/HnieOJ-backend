package com.hnieacm.discussion.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 新建讨论回答请求
 */
@Data
public class CreateDiscussionAnswerRequest {

    @NotBlank(message = "content 不能为空")
    private String content;
}
