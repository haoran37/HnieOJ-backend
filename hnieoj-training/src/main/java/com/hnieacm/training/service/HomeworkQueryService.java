package com.hnieacm.training.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.training.vo.HomeworkDetailVo;
import com.hnieacm.training.vo.HomeworkListVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 作业查询服务
 */
public interface HomeworkQueryService {

    PageVo<HomeworkListVo> listHomeworks(int page, int pageSize, String keyword);

    HomeworkDetailVo getHomeworkDetail(Long homeworkId);
}
