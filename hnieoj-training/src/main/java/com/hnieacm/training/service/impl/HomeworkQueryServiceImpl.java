package com.hnieacm.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.training.constant.HomeworkStatusConstant;
import com.hnieacm.training.entity.Homework;
import com.hnieacm.training.entity.HomeworkClass;
import com.hnieacm.training.entity.HomeworkProblem;
import com.hnieacm.training.mapper.HomeworkClassMapper;
import com.hnieacm.training.mapper.HomeworkMapper;
import com.hnieacm.training.mapper.HomeworkProblemMapper;
import com.hnieacm.training.service.HomeworkQueryService;
import com.hnieacm.training.vo.HomeworkDetailVo;
import com.hnieacm.training.vo.HomeworkListVo;
import com.hnieacm.training.vo.HomeworkProblemVo;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 和 pageSize 必须大于 0");
        }
        String normalizedKeyword = trimToNull(keyword);

        LambdaQueryWrapper<Homework> wrapper = new LambdaQueryWrapper<Homework>()
                .eq(Homework::getStatus, HomeworkStatusConstant.ENABLED)
                .orderByDesc(Homework::getStartTime)
                .orderByDesc(Homework::getId);
        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(Homework::getTitle, normalizedKeyword)
                    .or()
                    .like(Homework::getSource, normalizedKeyword));
        }

        Page<Homework> pageParam = new Page<>(page, pageSize);
        Page<Homework> pageResult = homeworkMapper.selectPage(pageParam, wrapper);
        List<Homework> records = pageResult.getRecords();
        if (records == null || records.isEmpty()) {
            return new PageVo<>(Collections.emptyList(), pageResult.getTotal());
        }

        Map<Long, Long> problemCountMap = queryHomeworkProblemCountMap(records);
        Map<Long, Long> classCountMap = queryHomeworkClassCountMap(records);

        List<HomeworkListVo> list = records.stream().map(homework -> {
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
        return new PageVo<>(list, pageResult.getTotal());
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
        Homework homework = queryEnabledHomework(homeworkId);

        List<Long> classIds = homeworkClassMapper.selectList(new LambdaQueryWrapper<HomeworkClass>()
                        .select(HomeworkClass::getClassId)
                        .eq(HomeworkClass::getHid, homework.getId())
                        .orderByAsc(HomeworkClass::getId))
                .stream()
                .map(HomeworkClass::getClassId)
                .toList();

        List<HomeworkProblemVo> problems = homeworkProblemMapper.selectList(new LambdaQueryWrapper<HomeworkProblem>()
                        .eq(HomeworkProblem::getHid, homework.getId())
                        .orderByAsc(HomeworkProblem::getDisplayId)
                        .orderByAsc(HomeworkProblem::getId))
                .stream()
                .map(this::toHomeworkProblemVo)
                .toList();
        return toHomeworkDetailVo(homework, classIds, problems);
    }

    /**
     * @MethodName queryEnabledHomework
     * @Param homeworkId
     * @Description 根据作业ID查询启用状态的作业信息，若作业不存在或未启用则抛出异常
     * @Return @return {@link Homework }
     * @Author HaoRan_Lyu
     * @Date 2026/02/24
     */
    private @NonNull Homework queryEnabledHomework(Long homeworkId) {
        if (homeworkId == null || homeworkId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "homeworkId 不合法");
        }
        Homework homework = homeworkMapper.selectOne(new LambdaQueryWrapper<Homework>()
                .eq(Homework::getId, homeworkId)
                .eq(Homework::getStatus, HomeworkStatusConstant.ENABLED)
                .last("limit 1"));
        if (homework == null) {
            throw new BizException(ResultCode.NOT_FOUND, "作业不存在或不可访问");
        }
        return homework;
    }

    /**
     * @MethodName queryHomeworkProblemCountMap
     * @Param homeworks
     * @Description 查询作业关联题目数量
     * @Return @return {@link Map }<{@link Long }, {@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/24
     */
    private Map<Long, Long> queryHomeworkProblemCountMap(List<Homework> homeworks) {
        List<Long> homeworkIds = homeworks.stream().map(Homework::getId).toList();
        List<HomeworkProblem> relations = homeworkProblemMapper.selectList(new LambdaQueryWrapper<HomeworkProblem>()
                .select(HomeworkProblem::getHid)
                .in(HomeworkProblem::getHid, homeworkIds));
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyMap();
        }
        return relations.stream().collect(Collectors.groupingBy(HomeworkProblem::getHid, Collectors.counting()));
    }

    /**
     * @MethodName queryHomeworkClassCountMap
     * @Param homeworks
     * @Description 查询作业关联班级数量
     * @Return @return {@link Map }<{@link Long }, {@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/24
     */
    private Map<Long, Long> queryHomeworkClassCountMap(List<Homework> homeworks) {
        List<Long> homeworkIds = homeworks.stream().map(Homework::getId).toList();
        List<HomeworkClass> relations = homeworkClassMapper.selectList(new LambdaQueryWrapper<HomeworkClass>()
                .select(HomeworkClass::getHid)
                .in(HomeworkClass::getHid, homeworkIds));
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyMap();
        }
        return relations.stream().collect(Collectors.groupingBy(HomeworkClass::getHid, Collectors.counting()));
    }

    /**
     * @MethodName toHomeworkProblemVo
     * @Param problem
     * @Description 将 HomeworkProblem 实体转换为 HomeworkProblemVo 对象
     * @Return @return {@link HomeworkProblemVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/24
     */
    private HomeworkProblemVo toHomeworkProblemVo(HomeworkProblem problem) {
        HomeworkProblemVo vo = new HomeworkProblemVo();
        vo.setId(problem.getId());
        vo.setProblemId(problem.getProblemId());
        vo.setDisplayId(problem.getDisplayId());
        return vo;
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

    /**
     * @MethodName trimToNull
     * @Param value
     * @Description 去除字符串首尾空格，若结果为空则返回null
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/24
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
