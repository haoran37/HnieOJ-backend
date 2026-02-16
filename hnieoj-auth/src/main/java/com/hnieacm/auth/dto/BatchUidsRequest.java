package com.hnieacm.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 批量 uid 参数
 */
@Data
public class BatchUidsRequest {

    @NotEmpty(message = "uids 不能为空")
    private List<String> uids;
}

