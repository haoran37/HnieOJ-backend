package com.hnieacm.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户提交记录转移请求
 */
@Data
public class TransferUserSubmissionsRequest {

    @NotBlank(message = "targetUid 不能为空")
    private String targetUid;

    private Boolean deleteOriginal;
}
