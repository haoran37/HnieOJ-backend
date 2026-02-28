package com.hnieacm.training.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.training.dto.AdminHomeworkSaveRequest;
import com.hnieacm.training.dto.AdminHomeworkStatusRequest;
import com.hnieacm.training.vo.AdminHomeworkDetailVo;
import com.hnieacm.training.vo.AdminHomeworkListVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 作业管理服务
 */
public interface HomeworkAdminService {

    PageVo<AdminHomeworkListVo> listHomeworks(int page, int pageSize, String keyword);

    void createHomework(AdminHomeworkSaveRequest request);

    void updateHomework(Long homeworkId, AdminHomeworkSaveRequest request);

    void deleteHomework(Long homeworkId);

    void changeHomeworkStatus(AdminHomeworkStatusRequest request);

    AdminHomeworkDetailVo getHomeworkDetail(Long homeworkId);
}
