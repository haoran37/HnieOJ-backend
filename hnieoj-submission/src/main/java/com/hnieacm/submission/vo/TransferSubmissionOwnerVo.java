package com.hnieacm.submission.vo;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 提交记录归属转移结果
 */
@Data
public class TransferSubmissionOwnerVo {

    private String sourceUid;

    private String targetUid;

    private Integer transferredCount;
}
