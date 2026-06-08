package com.hnieacm.submission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 提交记录归属转移请求
 */
@Data
public class TransferSubmissionOwnerRequest {

    @NotBlank(message = "sourceUid 不能为空")
    private String sourceUid;

    @NotBlank(message = "targetUid 不能为空")
    private String targetUid;

    @NotBlank(message = "targetUsername 不能为空")
    private String targetUsername;
}
