package com.hnieacm.judge.service;

import com.hnieacm.common.dto.JudgeTaskMessage;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/06
 * @Description: 判题任务消费服务
 */
public interface JudgeTaskConsumer {

    void consume(JudgeTaskMessage message);
}
