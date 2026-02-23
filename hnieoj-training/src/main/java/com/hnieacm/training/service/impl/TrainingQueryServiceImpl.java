package com.hnieacm.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.training.constant.TrainingAuthConstant;
import com.hnieacm.training.constant.TrainingStatusConstant;
import com.hnieacm.training.constant.TrainingTypeConstant;
import com.hnieacm.training.entity.Training;
import com.hnieacm.training.entity.TrainingCategory;
import com.hnieacm.training.entity.TrainingCategoryRel;
import com.hnieacm.training.entity.TrainingProblem;
import com.hnieacm.training.mapper.TrainingCategoryMapper;
import com.hnieacm.training.mapper.TrainingCategoryRelMapper;
import com.hnieacm.training.mapper.TrainingMapper;
import com.hnieacm.training.mapper.TrainingProblemMapper;
import com.hnieacm.training.service.TrainingQueryService;
import com.hnieacm.training.vo.TrainingDetailVo;
import com.hnieacm.training.vo.TrainingListVo;
import com.hnieacm.training.vo.TrainingProblemVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 题单查询服务实现
 */
@Service
@RequiredArgsConstructor
public class TrainingQueryServiceImpl implements TrainingQueryService {

    private final TrainingMapper trainingMapper;
    private final TrainingProblemMapper trainingProblemMapper;
    private final TrainingCategoryRelMapper trainingCategoryRelMapper;
    private final TrainingCategoryMapper trainingCategoryMapper;

    /**
     * @MethodName listTrainings
     * @Param page
     * @Param pageSize
     * @Param keyword
     * @Param type
     * @Param auth
     * @Description 题单列表
     * @Return @return {@link PageVo }<{@link TrainingListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/24
     */
    @Override
    public PageVo<TrainingListVo> listTrainings(int page, int pageSize, String keyword, String type, String auth) {
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 和 pageSize 必须大于 0");
        }

        String normalizedKeyword = trimToNull(keyword);
        String normalizedType = TrainingTypeConstant.normalize(type);
        String normalizedAuth = TrainingAuthConstant.normalize(auth);

        LambdaQueryWrapper<Training> wrapper = new LambdaQueryWrapper<Training>()
                .eq(Training::getStatus, TrainingStatusConstant.ENABLED)
                .orderByAsc(Training::getRank)
                .orderByDesc(Training::getId);

        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(Training::getTitle, normalizedKeyword)
                    .or()
                    .like(Training::getDescription, normalizedKeyword));
        }
        if (normalizedType != null) {
            wrapper.eq(Training::getType, normalizedType);
        }
        if (normalizedAuth != null) {
            wrapper.eq(Training::getAuth, normalizedAuth);
        }

        Page<Training> pageParam = new Page<>(page, pageSize);
        Page<Training> pageResult = trainingMapper.selectPage(pageParam, wrapper);
        List<Training> records = pageResult.getRecords();
        if (records == null || records.isEmpty()) {
            return new PageVo<>(Collections.emptyList(), pageResult.getTotal());
        }

        Map<Long, Long> problemCountMap = queryTrainingProblemCountMap(records);
        Map<Long, List<String>> categoryMap = queryTrainingCategoryMap(records);

        List<TrainingListVo> list = records.stream().map(training -> {
            TrainingListVo vo = new TrainingListVo();
            vo.setId(training.getId());
            vo.setTitle(training.getTitle());
            vo.setType(training.getType());
            vo.setAuth(training.getAuth());
            vo.setAuthor(training.getAuthor());
            vo.setStatus(training.getStatus());
            vo.setRank(training.getRank());
            vo.setProblemCount(Math.toIntExact(problemCountMap.getOrDefault(training.getId(), 0L)));
            vo.setCategories(categoryMap.getOrDefault(training.getId(), Collections.emptyList()));
            vo.setGmtCreate(training.getGmtCreate());
            return vo;
        }).toList();
        return new PageVo<>(list, pageResult.getTotal());
    }

    /**
     * @MethodName getTrainingDetail
     * @Param trainingId
     * @Description 获取题单细节
     * @Return @return {@link TrainingDetailVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/24
     */
    @Override
    public TrainingDetailVo getTrainingDetail(Long trainingId) {
        Training training = queryEnabledTraining(trainingId);
        Map<Long, Long> problemCountMap = queryTrainingProblemCountMap(List.of(training));
        Map<Long, List<String>> categoryMap = queryTrainingCategoryMap(List.of(training));

        TrainingDetailVo vo = new TrainingDetailVo();
        vo.setId(training.getId());
        vo.setTitle(training.getTitle());
        vo.setDescription(training.getDescription());
        vo.setAuthor(training.getAuthor());
        vo.setType(training.getType());
        vo.setAuth(training.getAuth());
        vo.setStatus(training.getStatus());
        vo.setRank(training.getRank());
        vo.setProblemCount(Math.toIntExact(problemCountMap.getOrDefault(training.getId(), 0L)));
        vo.setCategories(categoryMap.getOrDefault(training.getId(), Collections.emptyList()));
        vo.setGmtCreate(training.getGmtCreate());
        vo.setGmtModified(training.getGmtModified());
        return vo;
    }

    /**
     * @MethodName listTrainingProblems
     * @Param trainingId
     * @Param page
     * @Param pageSize
     * @Description 题单题目列表
     * @Return @return {@link PageVo }<{@link TrainingProblemVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/24
     */
    @Override
    public PageVo<TrainingProblemVo> listTrainingProblems(Long trainingId, int page, int pageSize) {
        Training training = queryEnabledTraining(trainingId);
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 和 pageSize 必须大于 0");
        }

        Page<TrainingProblem> pageParam = new Page<>(page, pageSize);
        Page<TrainingProblem> pageResult = trainingProblemMapper.selectPage(pageParam, new LambdaQueryWrapper<TrainingProblem>()
                .eq(TrainingProblem::getTid, training.getId())
                .orderByAsc(TrainingProblem::getDisplayId)
                .orderByAsc(TrainingProblem::getId));

        List<TrainingProblemVo> list = pageResult.getRecords().stream().map(problem -> {
            TrainingProblemVo vo = new TrainingProblemVo();
            vo.setId(problem.getId());
            vo.setProblemId(problem.getProblemId());
            vo.setDisplayId(problem.getDisplayId());
            return vo;
        }).toList();
        return new PageVo<>(list, pageResult.getTotal());
    }

    /**
     * @MethodName queryEnabledTraining
     * @Param trainingId
     * @Description 查询启用的题单
     * @Return @return {@link Training }
     * @Author HaoRan_Lyu
     * @Date 2026/02/24
     */
    private Training queryEnabledTraining(Long trainingId) {
        if (trainingId == null || trainingId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "trainingId 不合法");
        }
        Training training = trainingMapper.selectOne(new LambdaQueryWrapper<Training>()
                .eq(Training::getId, trainingId)
                .eq(Training::getStatus, TrainingStatusConstant.ENABLED)
                .last("limit 1"));
        if (training == null) {
            throw new BizException(ResultCode.NOT_FOUND, "题单不存在或不可访问");
        }
        return training;
    }

    /**
     * @MethodName queryTrainingProblemCountMap
     * @Param trainings
      * @Description 查询题单题目数量
     * @Return @return {@link Map }<{@link Long }, {@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/24
     */
    private Map<Long, Long> queryTrainingProblemCountMap(List<Training> trainings) {
        List<Long> trainingIds = trainings.stream().map(Training::getId).toList();
        if (trainingIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<TrainingProblem> relations = trainingProblemMapper.selectList(new LambdaQueryWrapper<TrainingProblem>()
                .select(TrainingProblem::getTid)
                .in(TrainingProblem::getTid, trainingIds));
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyMap();
        }
        return relations.stream().collect(Collectors.groupingBy(TrainingProblem::getTid, Collectors.counting()));
    }

    /**
     * @MethodName queryTrainingCategoryMap
     * @Param trainings
     * @Description 查询题单分类
     * @Return @return {@link Map }<{@link Long }, {@link List }<{@link String }>>
     * @Author HaoRan_Lyu
     * @Date 2026/02/24
     */
    private Map<Long, List<String>> queryTrainingCategoryMap(List<Training> trainings) {
        List<Long> trainingIds = trainings.stream().map(Training::getId).toList();
        if (trainingIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<TrainingCategoryRel> relList = trainingCategoryRelMapper.selectList(new LambdaQueryWrapper<TrainingCategoryRel>()
                .select(TrainingCategoryRel::getTid, TrainingCategoryRel::getCid)
                .in(TrainingCategoryRel::getTid, trainingIds));
        if (relList == null || relList.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> categoryIds = relList.stream()
                .map(TrainingCategoryRel::getCid)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<TrainingCategory> categories = trainingCategoryMapper.selectList(new LambdaQueryWrapper<TrainingCategory>()
                .select(TrainingCategory::getId, TrainingCategory::getName)
                .in(TrainingCategory::getId, categoryIds));
        Map<Long, String> categoryNameMap = categories.stream()
                .collect(Collectors.toMap(TrainingCategory::getId, TrainingCategory::getName, (a, b) -> a));

        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (TrainingCategoryRel rel : relList) {
            String categoryName = categoryNameMap.get(rel.getCid());
            if (categoryName == null) {
                continue;
            }
            result.computeIfAbsent(rel.getTid(), key -> new java.util.ArrayList<>()).add(categoryName);
        }
        return result;
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
