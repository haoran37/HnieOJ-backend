package com.hnieacm.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 更新用户 IP 限制请求
 */
@Data
public class UpdateUserIpRestrictionRequest {

    @NotNull(message = "ipRestricted 不能为空")
    private Boolean ipRestricted;

    private List<String> ipWhitelist;
}
