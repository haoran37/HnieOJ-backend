package com.hnieacm.submission.service;

import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.dto.ProblemBasicDto;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/06
 * @Description: 判题任务消息发布服务
 */
public interface JudgeTaskMessagePublisher {

    void publishAfterCommit(Judge judge, ProblemBasicDto problem);
}
