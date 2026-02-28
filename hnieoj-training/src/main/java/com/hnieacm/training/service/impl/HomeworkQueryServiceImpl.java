package com.hnieacm.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.training.constant.HomeworkStatusConstant;
import com.hnieacm.training.entity.Homework;
import com.hnieacm.training.mapper.HomeworkClassMapper;
import com.hnieacm.training.mapper.HomeworkMapper;
import com.hnieacm.training.mapper.HomeworkProblemMapper;
import com.hnieacm.training.service.HomeworkQueryService;
import com.hnieacm.training.vo.HomeworkDetailVo;
import com.hnieacm.training.vo.HomeworkListVo;
import com.hnieacm.training.vo.HomeworkProblemVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 作业查询服务实现
 */
@Service
@RequiredArgsConstructor
public class HomeworkQueryServiceImpl implements HomeworkQueryService {

    private final HomeworkMapper homeworkMapper;
    private final HomeworkClassMapper homeworkClassMapper;
    private final HomeworkProblemMapper homeworkProblemMapper;

    /**
     * @MethodName listHomeworks
     * @Param page
     * @Param pageSize
     * @Param keyword
     * @Description 作业列表
     * @Return @return {@link PageVo }<{@link HomeworkListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/24
     */
    @Override
    public PageVo<HomeworkListVo> listHomeworks(int page, int pageSize, String keyword) {
        String normalizedKeyword = HomeworkServiceSupport.trimToNull(keyword);

        LambdaQueryWrapper<Homework> wrapper = new LambdaQueryWrapper<Homework>()
                .eq(Homework::getStatus, HomeworkStatusConstant.ENABLED)
                .orderByDesc(Homework::getStartTime)
                .orderByDesc(Homework::getId);
        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(Homework::getTitle, normalizedKeyword)
                    .or()
                    .like(Homework::getSource, normalizedKeyword));
        }
        return HomeworkServiceSupport.buildHomeworkPageVo(
                homeworkMapper,
                homeworkProblemMapper,
                homeworkClassMapper,
                wrapper,
                page,
                pageSize,
                pageData -> {
                    List<Homework> records = pageData.records();
                    Map<Long, Long> problemCountMap = pageData.problemCountMap();
                    Map<Long, Long> classCountMap = pageData.classCountMap();
                    return records.stream().map(homework -> {
                        HomeworkListVo vo = new HomeworkListVo();
                        vo.setId(homework.getId());
                        vo.setTitle(homework.getTitle());
                        vo.setSource(homework.getSource());
                        vo.setAuthor(homework.getAuthor());
                        vo.setStatus(homework.getStatus());
                        vo.setStartTime(homework.getStartTime());
                        vo.setEndTime(homework.getEndTime());
                        vo.setProblemCount(Math.toIntExact(problemCountMap.getOrDefault(homework.getId(), 0L)));
                        vo.setClassCount(Math.toIntExact(classCountMap.getOrDefault(homework.getId(), 0L)));
                        return vo;
                    }).toList();
                });
    }

    /**
     * @MethodName getHomeworkDetail
     * @Param homeworkId
     * @Description 获取作业详细信息
     * @Return @return {@link HomeworkDetailVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/24
     */
    @Override
    public HomeworkDetailVo getHomeworkDetail(Long homeworkId) {
        Homework homework = HomeworkServiceSupport.queryEnabledHomework(homeworkMapper, homeworkId, "作业不存在或不可访问");
        List<Long> classIds = HomeworkServiceSupport.queryHomeworkClassIds(homeworkClassMapper, homework.getId());
        List<HomeworkProblemVo> problems = HomeworkServiceSupport.queryHomeworkProblems(homeworkProblemMapper, homework.getId());
        return toHomeworkDetailVo(homework, classIds, problems);
    }

    /**
     * @MethodName toHomeworkDetailVo
     * @Param homework
     * @Param classIds
     * @Param problems
     * @Description 构建作业详情展示对象
     * @Return @return {@link HomeworkDetailVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/24
     */
    private HomeworkDetailVo toHomeworkDetailVo(Homework homework,
                                                List<Long> classIds,
                                                List<HomeworkProblemVo> problems) {
        HomeworkDetailVo vo = new HomeworkDetailVo();
        vo.setId(homework.getId());
        vo.setTitle(homework.getTitle());
        vo.setDescription(homework.getDescription());
        vo.setSource(homework.getSource());
        vo.setAuthor(homework.getAuthor());
        vo.setStatus(homework.getStatus());
        vo.setStartTime(homework.getStartTime());
        vo.setEndTime(homework.getEndTime());
        vo.setClassIds(classIds);
        vo.setProblems(problems);
        vo.setGmtCreate(homework.getGmtCreate());
        vo.setGmtModified(homework.getGmtModified());
        return vo;
    }
}
