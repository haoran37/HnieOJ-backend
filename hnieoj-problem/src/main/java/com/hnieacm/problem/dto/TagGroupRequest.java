package com.hnieacm.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 标签分组请求
 */
@Data
public class TagGroupRequest {

    @NotBlank(message = "title 不能为空")
    private String title;

    @NotEmpty(message = "tags 不能为空")
    private List<String> tags;
}
