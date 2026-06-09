package com.hnieacm.problem.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 导出题目请求参数
 */
@Data
public class ExportProblemRequest {

    @NotEmpty(message = "ids 不能为空")
    @Size(max = 100, message = "单次最多导出 100 道题目")
    private List<Long> ids;
}
