package com.hnieacm.submission.service;

import com.hnieacm.submission.dto.JudgeResultEventRequest;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题结果回传服务
 */
public interface JudgeResultReportService {

    void handleEvent(String submissionId, JudgeResultEventRequest request);
}
