package com.hnieacm.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/24
 * @Description: 更新公告请求
 */
@Data
public class AnnouncementUpdateRequest {

    @NotBlank(message = "title 不能为空")
    @Size(max = 255, message = "title 长度不能超过 255")
    private String title;

    @NotBlank(message = "content 不能为空")
    private String content;

    @Min(value = 0, message = "status 只能为 0 或 1")
    @Max(value = 1, message = "status 只能为 0 或 1")
    private Integer status;
}
