package com.hnieacm.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 身份资料变更审核请求。同意时 reason 可选，驳回时必须非空（由业务层校验）。
 */
@Data
public class ProfileChangeReviewRequest {

    @Size(max = 1000, message = "reason 长度不能超过 1000")
    private String reason;
}
