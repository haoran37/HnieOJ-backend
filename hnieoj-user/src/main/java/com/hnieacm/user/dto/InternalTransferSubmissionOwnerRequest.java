package com.hnieacm.user.dto;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 内部提交记录归属转移请求
 */
@Data
public class InternalTransferSubmissionOwnerRequest {

    private String sourceUid;

    private String targetUid;

    private String targetUsername;
}
