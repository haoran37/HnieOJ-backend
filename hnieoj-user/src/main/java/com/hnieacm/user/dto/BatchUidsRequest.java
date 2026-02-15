package com.hnieacm.user.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 批量 UID 请求。
 */
@Data
public class BatchUidsRequest {

    @NotEmpty(message = "uids 不能为空")
    private List<String> uids;
}

