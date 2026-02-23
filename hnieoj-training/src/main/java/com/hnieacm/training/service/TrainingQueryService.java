package com.hnieacm.training.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.training.vo.TrainingDetailVo;
import com.hnieacm.training.vo.TrainingListVo;
import com.hnieacm.training.vo.TrainingProblemVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 题单查询服务
 */
public interface TrainingQueryService {

    PageVo<TrainingListVo> listTrainings(int page, int pageSize, String keyword, String type, String auth);

    TrainingDetailVo getTrainingDetail(Long trainingId);

    PageVo<TrainingProblemVo> listTrainingProblems(Long trainingId, int page, int pageSize);
}
