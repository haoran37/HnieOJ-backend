package com.hnieacm.discussion.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 点赞/点踩请求
 */
@Data
public class DiscussionVoteRequest {

    @NotBlank(message = "direction 不能为空")
    private String direction;
}
