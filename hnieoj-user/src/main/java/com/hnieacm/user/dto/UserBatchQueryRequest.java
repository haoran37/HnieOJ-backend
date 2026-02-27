package com.hnieacm.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 批量查询用户基础信息请求
 */
@Data
public class UserBatchQueryRequest {

    @NotEmpty(message = "uids 不能为空")
    private List<@NotNull(message = "uid 不能为空") String> uids;
}
