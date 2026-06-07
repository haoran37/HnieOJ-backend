package com.hnieacm.submission.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.submission.dto.JudgeTaskOutboxQueryRequest;
import com.hnieacm.submission.vo.JudgeTaskOutboxVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题任务 outbox 服务
 */
public interface JudgeTaskOutboxService {

    PageVo<JudgeTaskOutboxVo> list(JudgeTaskOutboxQueryRequest request);

    void retry(Long id);
}
