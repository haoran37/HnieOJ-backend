package com.hnieacm.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 创建标签请求
 */
@Data
public class TagCreateRequest {

    @NotBlank(message = "name 不能为空")
    @Size(max = 50, message = "name 长度不能超过 50")
    private String name;

    @Size(max = 20, message = "color 长度不能超过 20")
    private String color;

    @Size(max = 50, message = "category 长度不能超过 50")
    private String category;
}
