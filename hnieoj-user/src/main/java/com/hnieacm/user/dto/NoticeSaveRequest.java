package com.hnieacm.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 创建/更新定向通知草稿请求
 */
@Data
public class NoticeSaveRequest {

    @NotBlank(message = "title 不能为空")
    @Size(max = 255, message = "title 长度不能超过 255")
    private String title;

    @NotBlank(message = "content 不能为空")
    @Size(max = 20000, message = "content 长度不能超过 20000")
    private String content;

    /**
     * USERS / CLASSES
     */
    @NotBlank(message = "targetType 不能为空")
    private String targetType;

    @NotEmpty(message = "targetIds 不能为空")
    @Size(max = 1000, message = "targetIds 数量不能超过 1000")
    private List<String> targetIds;
}
