package com.hnieacm.user.vo;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户提交记录转移结果
 */
@Data
public class TransferUserSubmissionsVo {

    private String sourceUid;

    private String targetUid;

    private Integer transferredCount;
}
