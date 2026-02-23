package com.hnieacm.discussion.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 新建讨论评论请求
 */
@Data
public class CreateDiscussionCommentRequest {

    @NotBlank(message = "content 不能为空")
    private String content;

    private String replyToUid;

    private String replyToName;
}
