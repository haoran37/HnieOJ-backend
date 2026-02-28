package com.hnieacm.training.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.training.constant.TrainingAuthConstant;
import com.hnieacm.training.constant.TrainingStatusConstant;
import com.hnieacm.training.constant.TrainingTypeConstant;
import com.hnieacm.training.dto.AdminTrainingProblemRequest;
import com.hnieacm.training.dto.AdminTrainingSaveRequest;
import com.hnieacm.training.dto.AdminTrainingStatusRequest;
import com.hnieacm.training.entity.Training;
import com.hnieacm.training.entity.TrainingCategoryRel;
import com.hnieacm.training.entity.TrainingProblem;
import com.hnieacm.training.mapper.TrainingCategoryRelMapper;
import com.hnieacm.training.mapper.TrainingMapper;
import com.hnieacm.training.mapper.TrainingProblemMapper;
import com.hnieacm.training.service.TrainingAdminService;
import com.hnieacm.training.service.manager.TrainingProblemManager;
import com.hnieacm.training.vo.AdminTrainingListVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 题单管理服务实现
 */
@Service
@RequiredArgsConstructor
public class TrainingAdminServiceImpl implements TrainingAdminService {

    private static final int DEFAULT_RANK = 0;

    private final TrainingMapper trainingMapper;
    private final TrainingProblemMapper trainingProblemMapper;
    private final TrainingCategoryRelMapper trainingCategoryRelMapper;
    private final TrainingProblemManager trainingProblemManager;

    /**
     * @MethodName listTrainings
     * @Param page
     * @Param pageSize
     * @Param keyword
     * @Param type
     * @Param auth
     * @Param status
     * @Description 题单列表
     * @Return @return {@link PageVo }<{@link AdminTrainingListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    public PageVo<AdminTrainingListVo> listTrainings(int page, int pageSize, String keyword, String type, String auth, Boolean status) {
        String normalizedKeyword = TrainingServiceSupport.trimToNull(keyword);
        String normalizedType = TrainingTypeConstant.normalize(type);
        String normalizedAuth = TrainingAuthConstant.normalize(auth);

        LambdaQueryWrapper<Training> wrapper = new LambdaQueryWrapper<Training>()
                .orderByAsc(Training::getRank)
                .orderByDesc(Training::getId);
        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(Training::getTitle, normalizedKeyword)
                    .or()
                    .like(Training::getDescription, normalizedKeyword)
                    .or()
                    .like(Training::getAuthor, normalizedKeyword));
        }
        if (normalizedType != null) {
            wrapper.eq(Training::getType, normalizedType);
        }
        if (normalizedAuth != null) {
            wrapper.eq(Training::getAuth, normalizedAuth);
        }
        if (status != null) {
            wrapper.eq(Training::getStatus, TrainingStatusConstant.toDbStatus(status));
        }

        return TrainingServiceSupport.buildTrainingPageVo(
                trainingMapper,
                trainingProblemMapper,
                wrapper,
                page,
                pageSize,
                pageData -> {
                    List<Training> records = pageData.records();
                    Map<Long, Long> problemCountMap = pageData.problemCountMap();
                    return records.stream().map(training -> {
                        AdminTrainingListVo vo = new AdminTrainingListVo();
                        vo.setId(training.getId());
                        vo.setTitle(training.getTitle());
                        vo.setType(training.getType());
                        vo.setAuth(training.getAuth());
                        vo.setAuthor(training.getAuthor());
                        vo.setStatus(TrainingStatusConstant.isEnabled(training.getStatus()));
                        vo.setRank(training.getRank());
                        vo.setProblemCount(Math.toIntExact(problemCountMap.getOrDefault(training.getId(), 0L)));
                        vo.setGmtCreate(training.getGmtCreate());
                        return vo;
                    }).toList();
                });
    }

    /**
     * @MethodName createTraining
     * @Param request
     * @Description 创建题单
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTraining(AdminTrainingSaveRequest request) {
        validateSaveRequest(request);

        Training training = new Training();
        training.setAuthor(StpUtil.getLoginIdAsString());
        fillTrainingEntity(request, training);
        trainingMapper.insert(training);

        replaceTrainingProblems(training.getId(), request.getProblems());
    }

    /**
     * @MethodName updateTraining
     * @Param trainingId
     * @Param request
     * @Description 更新题单
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTraining(Long trainingId, AdminTrainingSaveRequest request) {
        Training existed = getTrainingById(trainingId);
        validateSaveRequest(request);

        Training training = new Training();
        training.setId(trainingId);
        training.setAuthor(existed.getAuthor());
        training.setGmtCreate(existed.getGmtCreate());
        fillTrainingEntity(request, training);
        trainingMapper.updateById(training);

        replaceTrainingProblems(trainingId, request.getProblems());
    }

    /**
     * @MethodName deleteTraining
     * @Param trainingId
     * @Description 删除题单
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTraining(Long trainingId) {
        getTrainingById(trainingId);
        trainingMapper.deleteById(trainingId);
        trainingProblemMapper.delete(new LambdaQueryWrapper<TrainingProblem>()
                .eq(TrainingProblem::getTid, trainingId));
        trainingCategoryRelMapper.delete(new LambdaQueryWrapper<TrainingCategoryRel>()
                .eq(TrainingCategoryRel::getTid, trainingId));
    }

    /**
     * @MethodName changeTrainingStatus
     * @Param request
     * @Description 修改题单状态
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    public void changeTrainingStatus(AdminTrainingStatusRequest request) {
        Training training = getTrainingById(request.getId());
        training.setStatus(TrainingStatusConstant.toDbStatus(request.getStatus()));
        trainingMapper.updateById(training);
    }

    /**
     * @MethodName validateSaveRequest
     * @Param request
     * @Description 验证保存请求
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private void validateSaveRequest(AdminTrainingSaveRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
    }

    /**
     * @MethodName fillTrainingEntity
     * @Param request
     * @Param training
     * @Description 填充题单实体
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private void fillTrainingEntity(AdminTrainingSaveRequest request, Training training) {
        String normalizedType = TrainingTypeConstant.normalize(request.getType());
        if (normalizedType == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "type 参数不合法");
        }

        String normalizedAuth = TrainingAuthConstant.normalize(request.getAuth());
        if (normalizedAuth == null) {
            normalizedAuth = TrainingAuthConstant.PUBLIC;
        }
        String privatePwd = TrainingServiceSupport.trimToNull(request.getPrivatePwd());
        if (TrainingAuthConstant.PRIVATE.equals(normalizedAuth) && privatePwd == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "私有题单必须设置 privatePwd");
        }
        if (!TrainingAuthConstant.PRIVATE.equals(normalizedAuth)) {
            privatePwd = null;
        }

        training.setTitle(request.getTitle().trim());
        training.setType(normalizedType);
        training.setAuth(normalizedAuth);
        training.setPrivatePwd(privatePwd);
        training.setDescription(TrainingServiceSupport.trimToNull(request.getDescription()));
        training.setStatus(TrainingStatusConstant.toDbStatus(request.getStatus()));
        training.setRank(request.getRank() == null ? DEFAULT_RANK : request.getRank());
    }

    /**
     * @MethodName replaceTrainingProblems
     * @Param trainingId
     * @Param problems
     * @Description 替换题单题目
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private void replaceTrainingProblems(Long trainingId, List<AdminTrainingProblemRequest> problems) {
        trainingProblemMapper.delete(new LambdaQueryWrapper<TrainingProblem>()
                .eq(TrainingProblem::getTid, trainingId));

        List<AdminTrainingProblemRequest> normalizedProblems = normalizeProblems(problems);
        if (normalizedProblems.isEmpty()) {
            return;
        }

        List<Long> problemIds = normalizedProblems.stream()
                .map(AdminTrainingProblemRequest::getProblemId)
                .toList();
        trainingProblemManager.ensureProblemsExist(problemIds);

        Set<Integer> usedDisplayIds = new LinkedHashSet<>();
        int nextDisplayId = 1;
        for (AdminTrainingProblemRequest problem : normalizedProblems) {
            Integer displayId = problem.getDisplayId();
            if (displayId != null) {
                if (!usedDisplayIds.add(displayId)) {
                    throw new BizException(ResultCode.BAD_REQUEST, "displayId 不能重复: " + displayId);
                }
            } else {
                while (usedDisplayIds.contains(nextDisplayId)) {
                    nextDisplayId++;
                }
                displayId = nextDisplayId;
                usedDisplayIds.add(displayId);
                nextDisplayId++;
            }

            TrainingProblem relation = new TrainingProblem();
            relation.setTid(trainingId);
            relation.setProblemId(problem.getProblemId());
            relation.setDisplayId(displayId);
            trainingProblemMapper.insert(relation);
        }
    }

    /**
     * @MethodName normalizeProblems
     * @Param problems
     * @Description 标准化题目
     * @Return @return {@link List }<{@link AdminTrainingProblemRequest }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private List<AdminTrainingProblemRequest> normalizeProblems(List<AdminTrainingProblemRequest> problems) {
        if (problems == null || problems.isEmpty()) {
            return Collections.emptyList();
        }
        List<AdminTrainingProblemRequest> result = new ArrayList<>();
        Set<Long> problemIdSet = new LinkedHashSet<>();
        for (AdminTrainingProblemRequest problem : problems) {
            if (problem == null) {
                continue;
            }
            Long problemId = problem.getProblemId();
            if (problemId == null || problemId <= 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "problemId 不能为空且必须大于 0");
            }
            if (!problemIdSet.add(problemId)) {
                throw new BizException(ResultCode.BAD_REQUEST, "problemId 不能重复: " + problemId);
            }
            result.add(problem);
        }
        return result;
    }

    /**
     * @MethodName getTrainingById
     * @Param trainingId
     * @Description 通过 id 获得题单
     * @Return @return {@link Training }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private Training getTrainingById(Long trainingId) {
        if (trainingId == null || trainingId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "trainingId 不合法");
        }
        Training training = trainingMapper.selectById(trainingId);
        if (training == null) {
            throw new BizException(ResultCode.NOT_FOUND, "题单不存在");
        }
        return training;
    }

}
