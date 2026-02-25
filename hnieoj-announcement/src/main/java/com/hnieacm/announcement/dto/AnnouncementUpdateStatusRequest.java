package com.hnieacm.announcement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/24
 * @Description: 更新公告状态请求
 */
@Data
public class AnnouncementUpdateStatusRequest {

    @NotNull(message = "status 不能为空")
    @Min(value = 0, message = "status 只能为 0 或 1")
    @Max(value = 1, message = "status 只能为 0 或 1")
    private Integer status;
}
