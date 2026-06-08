package com.hnieacm.problem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 保存标签配置请求
 */
@Data
public class SaveTagConfigRequest {

    @Valid
    @NotEmpty(message = "tags 不能为空")
    private List<TagGroupRequest> tags;
}
