package com.hnieacm.submission.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.submission.dto.CreateRejudgeTaskRequest;
import com.hnieacm.submission.dto.RejudgeTaskQueryRequest;
import com.hnieacm.submission.vo.RejudgeTaskVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 重判任务服务
 */
public interface RejudgeTaskService {

    RejudgeTaskVo create(CreateRejudgeTaskRequest request);

    PageVo<RejudgeTaskVo> list(RejudgeTaskQueryRequest request);
}
