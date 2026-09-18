package com.hnieacm.judge.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 设置判题节点 draining 状态请求
 */
@Data
public class JudgeNodeDrainingRequest {

    @NotNull(message = "draining 不能为空")
    private Boolean draining;
}
