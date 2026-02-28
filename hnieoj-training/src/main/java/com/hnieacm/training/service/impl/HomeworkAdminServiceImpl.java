package com.hnieacm.training.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.training.constant.HomeworkStatusConstant;
import com.hnieacm.training.dto.AdminHomeworkProblemRequest;
import com.hnieacm.training.dto.AdminHomeworkSaveRequest;
import com.hnieacm.training.dto.AdminHomeworkStatusRequest;
import com.hnieacm.training.entity.Homework;
import com.hnieacm.training.entity.HomeworkClass;
import com.hnieacm.training.entity.HomeworkProblem;
import com.hnieacm.training.mapper.HomeworkClassMapper;
import com.hnieacm.training.mapper.HomeworkMapper;
import com.hnieacm.training.mapper.HomeworkProblemMapper;
import com.hnieacm.training.service.HomeworkAdminService;
import com.hnieacm.training.service.manager.TrainingProblemManager;
import com.hnieacm.training.vo.AdminHomeworkDetailVo;
import com.hnieacm.training.vo.AdminHomeworkListVo;
import com.hnieacm.training.vo.HomeworkProblemVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 作业管理服务实现
 */
@Service
@RequiredArgsConstructor
public class HomeworkAdminServiceImpl implements HomeworkAdminService {

    private final HomeworkMapper homeworkMapper;
    private final HomeworkClassMapper homeworkClassMapper;
    private final HomeworkProblemMapper homeworkProblemMapper;
    private final TrainingProblemManager trainingProblemManager;

    /**
     * @MethodName listHomeworks
     * @Param page
     * @Param pageSize
     * @Param keyword
     * @Description 管理端作业列表
     * @Return @return {@link PageVo }<{@link AdminHomeworkListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    public PageVo<AdminHomeworkListVo> listHomeworks(int page, int pageSize, String keyword) {
        String normalizedKeyword = HomeworkServiceSupport.trimToNull(keyword);
        LambdaQueryWrapper<Homework> wrapper = new LambdaQueryWrapper<Homework>()
                .orderByDesc(Homework::getStartTime)
                .orderByDesc(Homework::getId);
        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(Homework::getTitle, normalizedKeyword)
                    .or()
                    .like(Homework::getSource, normalizedKeyword)
                    .or()
                    .like(Homework::getAuthor, normalizedKeyword));
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
                        AdminHomeworkListVo vo = new AdminHomeworkListVo();
                        vo.setId(homework.getId());
                        vo.setTitle(homework.getTitle());
                        vo.setSource(homework.getSource());
                        vo.setAuthor(homework.getAuthor());
                        vo.setStatus(HomeworkStatusConstant.isEnabled(homework.getStatus()));
                        vo.setStartTime(homework.getStartTime());
                        vo.setEndTime(homework.getEndTime());
                        vo.setProblemCount(Math.toIntExact(problemCountMap.getOrDefault(homework.getId(), 0L)));
                        vo.setClassCount(Math.toIntExact(classCountMap.getOrDefault(homework.getId(), 0L)));
                        vo.setGmtCreate(homework.getGmtCreate());
                        return vo;
                    }).toList();
                });
    }

    /**
     * @MethodName createHomework
     * @Param request
     * @Description 新增作业
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createHomework(AdminHomeworkSaveRequest request) {
        validateSaveRequest(request);

        Homework homework = new Homework();
        homework.setAuthor(StpUtil.getLoginIdAsString());
        fillHomeworkEntity(request, homework);
        homeworkMapper.insert(homework);

        replaceHomeworkClasses(homework.getId(), request.getClassIds());
        replaceHomeworkProblems(homework.getId(), request.getProblems());
    }

    /**
     * @MethodName updateHomework
     * @Param homeworkId
     * @Param request
     * @Description 编辑作业
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateHomework(Long homeworkId, AdminHomeworkSaveRequest request) {
        Homework existed = getHomeworkById(homeworkId);
        validateSaveRequest(request);

        Homework homework = new Homework();
        homework.setId(homeworkId);
        homework.setAuthor(existed.getAuthor());
        homework.setGmtCreate(existed.getGmtCreate());
        fillHomeworkEntity(request, homework);
        homeworkMapper.updateById(homework);

        replaceHomeworkClasses(homeworkId, request.getClassIds());
        replaceHomeworkProblems(homeworkId, request.getProblems());
    }

    /**
     * @MethodName deleteHomework
     * @Param homeworkId
     * @Description 删除作业
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteHomework(Long homeworkId) {
        getHomeworkById(homeworkId);
        homeworkMapper.deleteById(homeworkId);
        homeworkClassMapper.delete(new LambdaQueryWrapper<HomeworkClass>()
                .eq(HomeworkClass::getHid, homeworkId));
        homeworkProblemMapper.delete(new LambdaQueryWrapper<HomeworkProblem>()
                .eq(HomeworkProblem::getHid, homeworkId));
    }

    /**
     * @MethodName changeHomeworkStatus
     * @Param request
     * @Description 切换作业状态
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    public void changeHomeworkStatus(AdminHomeworkStatusRequest request) {
        Homework homework = getHomeworkById(request.getId());
        homework.setStatus(HomeworkStatusConstant.toDbStatus(request.getStatus()));
        homeworkMapper.updateById(homework);
    }

    /**
     * @MethodName getHomeworkDetail
     * @Param homeworkId
     * @Description 获取管理端作业详情
     * @Return @return {@link AdminHomeworkDetailVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    public AdminHomeworkDetailVo getHomeworkDetail(Long homeworkId) {
        Homework homework = getHomeworkById(homeworkId);
        List<Long> classIds = HomeworkServiceSupport.queryHomeworkClassIds(homeworkClassMapper, homeworkId);
        List<HomeworkProblemVo> problems = HomeworkServiceSupport.queryHomeworkProblems(homeworkProblemMapper, homeworkId);

        AdminHomeworkDetailVo vo = new AdminHomeworkDetailVo();
        vo.setId(homework.getId());
        vo.setTitle(homework.getTitle());
        vo.setDescription(homework.getDescription());
        vo.setSource(homework.getSource());
        vo.setAuthor(homework.getAuthor());
        vo.setStatus(HomeworkStatusConstant.isEnabled(homework.getStatus()));
        vo.setStartTime(homework.getStartTime());
        vo.setEndTime(homework.getEndTime());
        vo.setTimeRange(buildTimeRange(homework.getStartTime(), homework.getEndTime()));
        vo.setClassIds(classIds);
        vo.setProblems(problems);
        vo.setGmtCreate(homework.getGmtCreate());
        vo.setGmtModified(homework.getGmtModified());
        return vo;
    }

    /**
     * @MethodName validateSaveRequest
     * @Param request
     * @Description 校验保存请求
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private void validateSaveRequest(AdminHomeworkSaveRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
    }

    /**
     * @MethodName fillHomeworkEntity
     * @Param request
     * @Param homework
     * @Description 填充作业实体
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private void fillHomeworkEntity(AdminHomeworkSaveRequest request, Homework homework) {
        LocalDateTime startTime = HomeworkServiceSupport.toLocalDateTime(request.getStartTime());
        LocalDateTime endTime = HomeworkServiceSupport.toLocalDateTime(request.getEndTime());
        if (startTime == null || endTime == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "startTime/endTime 不合法");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BizException(ResultCode.BAD_REQUEST, "endTime 必须晚于 startTime");
        }

        homework.setTitle(request.getTitle().trim());
        homework.setSource(HomeworkServiceSupport.trimToNull(request.getSource()));
        homework.setDescription(HomeworkServiceSupport.trimToNull(request.getDescription()));
        homework.setStatus(HomeworkStatusConstant.toDbStatus(request.getStatus()));
        homework.setStartTime(startTime);
        homework.setEndTime(endTime);
    }

    /**
     * @MethodName replaceHomeworkClasses
     * @Param homeworkId
     * @Param classIds
     * @Description 替换作业关联班级
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private void replaceHomeworkClasses(Long homeworkId, List<Long> classIds) {
        homeworkClassMapper.delete(new LambdaQueryWrapper<HomeworkClass>()
                .eq(HomeworkClass::getHid, homeworkId));

        List<Long> normalizedClassIds = normalizeClassIds(classIds);
        if (normalizedClassIds.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "classIds 不能为空");
        }
        for (Long classId : normalizedClassIds) {
            HomeworkClass relation = new HomeworkClass();
            relation.setHid(homeworkId);
            relation.setClassId(classId);
            homeworkClassMapper.insert(relation);
        }
    }

    /**
     * @MethodName replaceHomeworkProblems
     * @Param homeworkId
     * @Param problems
     * @Description 替换作业题目
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private void replaceHomeworkProblems(Long homeworkId, List<AdminHomeworkProblemRequest> problems) {
        homeworkProblemMapper.delete(new LambdaQueryWrapper<HomeworkProblem>()
                .eq(HomeworkProblem::getHid, homeworkId));

        List<AdminHomeworkProblemRequest> normalizedProblems = normalizeProblems(problems);
        if (normalizedProblems.isEmpty()) {
            return;
        }

        List<Long> problemIds = normalizedProblems.stream()
                .map(AdminHomeworkProblemRequest::getProblemId)
                .toList();
        trainingProblemManager.ensureProblemsExist(problemIds);

        Set<Integer> usedDisplayIds = new LinkedHashSet<>();
        int autoDisplayIdIndex = 0;
        for (AdminHomeworkProblemRequest problem : normalizedProblems) {
            String displayIdCode = HomeworkServiceSupport.normalizeDisplayIdCode(problem.getDisplayId());
            Integer displayId;
            if (displayIdCode != null) {
                displayId = HomeworkServiceSupport.toDisplayIdNumber(displayIdCode);
                if (!usedDisplayIds.add(displayId)) {
                    throw new BizException(ResultCode.BAD_REQUEST, "displayId 不能重复: " + displayIdCode);
                }
            } else {
                do {
                    String autoDisplayIdCode = HomeworkServiceSupport.buildDisplayIdByIndex(autoDisplayIdIndex);
                    displayId = HomeworkServiceSupport.toDisplayIdNumber(autoDisplayIdCode);
                    autoDisplayIdIndex++;
                } while (usedDisplayIds.contains(displayId));
                if (!usedDisplayIds.add(displayId)) {
                    throw new BizException(ResultCode.BAD_REQUEST, "displayId 自动生成失败");
                }
            }

            HomeworkProblem relation = new HomeworkProblem();
            relation.setHid(homeworkId);
            relation.setProblemId(problem.getProblemId());
            relation.setDisplayId(displayId);
            homeworkProblemMapper.insert(relation);
        }
    }

    /**
     * @MethodName normalizeClassIds
     * @Param classIds
     * @Description 标准化班级 id 列表
     * @Return @return {@link List }<{@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private List<Long> normalizeClassIds(List<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> classIdSet = new LinkedHashSet<>();
        for (Long classId : classIds) {
            if (classId == null || classId <= 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "classId 不能为空且必须大于 0");
            }
            classIdSet.add(classId);
        }
        return classIdSet.stream().toList();
    }

    /**
     * @MethodName normalizeProblems
     * @Param problems
     * @Description 标准化题目参数
     * @Return @return {@link List }<{@link AdminHomeworkProblemRequest }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private List<AdminHomeworkProblemRequest> normalizeProblems(List<AdminHomeworkProblemRequest> problems) {
        return trainingProblemManager.normalizeProblemRequests(problems, AdminHomeworkProblemRequest::getProblemId);
    }

    /**
     * @MethodName getHomeworkById
     * @Param homeworkId
     * @Description 根据 id 查询作业
     * @Return @return {@link Homework }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private Homework getHomeworkById(Long homeworkId) {
        return HomeworkServiceSupport.queryHomeworkById(homeworkMapper, homeworkId, "作业不存在");
    }

    /**
     * @MethodName buildTimeRange
     * @Param startTime
     * @Param endTime
     * @Description 构建时间范围
     * @Return @return {@link List }<{@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private List<Long> buildTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return Collections.emptyList();
        }
        return List.of(HomeworkServiceSupport.toEpochMilli(startTime), HomeworkServiceSupport.toEpochMilli(endTime));
    }
}
