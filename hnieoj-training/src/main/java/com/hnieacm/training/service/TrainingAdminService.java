package com.hnieacm.training.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.training.dto.AdminTrainingSaveRequest;
import com.hnieacm.training.dto.AdminTrainingStatusRequest;
import com.hnieacm.training.vo.AdminTrainingListVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 题单管理服务
 */
public interface TrainingAdminService {

    PageVo<AdminTrainingListVo> listTrainings(int page, int pageSize, String keyword, String type, String auth, Boolean status);

    void createTraining(AdminTrainingSaveRequest request);

    void updateTraining(Long trainingId, AdminTrainingSaveRequest request);

    void deleteTraining(Long trainingId);

    void changeTrainingStatus(AdminTrainingStatusRequest request);
}
