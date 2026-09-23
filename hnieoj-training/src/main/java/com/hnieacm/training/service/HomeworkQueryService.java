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
    PageVo<HomeworkListVo> listHomeworks(int page, int pageSize, String keyword, Long classId);
    /**
     * List homeworks, optionally filtering by one or more classes.
     * @param page page number
     * @param pageSize page size
     * @param keyword search keyword
     * @param classId single class identifier
     * @param classIds class identifiers
     * @return matching homework page
     */
    PageVo<HomeworkListVo> listHomeworks(int page, int pageSize, String keyword, Long classId, java.util.List<Long> classIds);

    HomeworkDetailVo getHomeworkDetail(Long homeworkId);
}
