package com.hnieacm.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.training.entity.Training;
import com.hnieacm.training.entity.TrainingProblem;
import com.hnieacm.training.mapper.TrainingMapper;
import com.hnieacm.training.mapper.TrainingProblemMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 题单服务通用支撑方法
 */
public final class TrainingServiceSupport {

    private TrainingServiceSupport() {
    }

    /**
     * @MethodName validatePageParams
     * @Param page
     * @Param pageSize
     * @Description 验证页面参数
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static void validatePageParams(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 和 pageSize 必须大于 0");
        }
    }

    /**
     * @MethodName queryTrainingPageData
     * @Param trainingMapper
     * @Param trainingProblemMapper
     * @Param wrapper
     * @Param page
     * @Param pageSize
     * @Description 查询题单页面数据
     * @Return @return {@link TrainingPageData }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static TrainingPageData queryTrainingPageData(TrainingMapper trainingMapper,
                                                         TrainingProblemMapper trainingProblemMapper,
                                                         LambdaQueryWrapper<Training> wrapper,
                                                         int page,
                                                         int pageSize) {
        validatePageParams(page, pageSize);

        Page<Training> pageParam = new Page<>(page, pageSize);
        Page<Training> pageResult = trainingMapper.selectPage(pageParam, wrapper);
        List<Training> records = pageResult.getRecords();
        if (records == null || records.isEmpty()) {
            return new TrainingPageData(Collections.emptyList(), pageResult.getTotal(), Collections.emptyMap());
        }
        Map<Long, Long> problemCountMap = queryTrainingProblemCountMap(trainingProblemMapper, records);
        return new TrainingPageData(records, pageResult.getTotal(), problemCountMap);
    }

    /**
     * @MethodName queryTrainingProblemCountMap
     * @Param trainingProblemMapper
     * @Param trainings
     * @Description 查询题单题目数量
     * @Return @return {@link Map }<{@link Long }, {@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static Map<Long, Long> queryTrainingProblemCountMap(TrainingProblemMapper trainingProblemMapper, List<Training> trainings) {
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
     * @MethodName emptyPageVo
     * @Param total
     * @Description 空页vo
     * @Return @return {@link PageVo }<{@link T }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static <T> PageVo<T> emptyPageVo(long total) {
        return new PageVo<>(Collections.emptyList(), total);
    }

    /**
     * @MethodName buildTrainingPageVo
     * @Param trainingMapper
     * @Param trainingProblemMapper
     * @Param wrapper
     * @Param page
     * @Param pageSize
     * @Param converter
     * @Description 构建题单页面vo
     * @Return @return {@link PageVo }<{@link T }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static <T> PageVo<T> buildTrainingPageVo(TrainingMapper trainingMapper,
                                                    TrainingProblemMapper trainingProblemMapper,
                                                    LambdaQueryWrapper<Training> wrapper,
                                                    int page,
                                                    int pageSize,
                                                    Function<TrainingPageData, List<T>> converter) {
        TrainingPageData pageData = queryTrainingPageData(trainingMapper, trainingProblemMapper, wrapper, page, pageSize);
        if (pageData.records().isEmpty()) {
            return emptyPageVo(pageData.total());
        }
        List<T> list = converter.apply(pageData);
        return new PageVo<>(list, pageData.total());
    }

    /**
     * @MethodName trimToNull
     * @Param value
     * @Description 将字符串去除首尾空格，若结果为空则返回null，否则返回处理后的字符串
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 题单分页查询结果
     */
    public record TrainingPageData(List<Training> records, long total, Map<Long, Long> problemCountMap) {
    }
}
