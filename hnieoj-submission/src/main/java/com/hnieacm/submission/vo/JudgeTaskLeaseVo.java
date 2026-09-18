package com.hnieacm.submission.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 判题任务续租响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JudgeTaskLeaseVo {

    private Long leaseUntil;
}
