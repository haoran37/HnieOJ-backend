package com.hnieacm.training.service.manager;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.training.feign.ProblemInternalFeignClient;
import com.hnieacm.training.feign.dto.ProblemBasicInfoDto;
import com.hnieacm.training.feign.dto.ProblemBatchQueryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 题目远程查询管理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainingProblemManager {

    private final ProblemInternalFeignClient problemInternalFeignClient;

    /**
     * @MethodName queryProblemMap
     * @Param problemIds
     * @Description 批量查询题目基础信息
     * @Return @return {@link Map }<{@link Long }, {@link ProblemBasicInfoDto }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public Map<Long, ProblemBasicInfoDto> queryProblemMap(List<Long> problemIds) {
        List<Long> normalizedIds = normalizeProblemIds(problemIds);
        if (normalizedIds.isEmpty()) {
            return Collections.emptyMap();
        }

        ProblemBatchQueryRequest request = new ProblemBatchQueryRequest();
        request.setIds(normalizedIds);
        Result<List<ProblemBasicInfoDto>> result;
        try {
            result = problemInternalFeignClient.queryProblemBasicByIds(request);
        } catch (Exception e) {
            log.error("Query problem basic info failed, problemIds: {}", normalizedIds, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "查询题目信息失败，请稍后重试");
        }

        if (result == null || result.getCode() != ResultCode.SUCCESS) {
            log.warn("Query problem basic info returned abnormal result, result: {}", result);
            throw new BizException(ResultCode.INTERNAL_ERROR, "查询题目信息失败，请稍后重试");
        }

        List<ProblemBasicInfoDto> problems = result.getData();
        if (problems == null || problems.isEmpty()) {
            return Collections.emptyMap();
        }

        return problems.stream()
                .filter(Objects::nonNull)
                .filter(problem -> problem.getId() != null)
                .collect(Collectors.toMap(ProblemBasicInfoDto::getId,
                        problem -> problem,
                        (oldValue, newValue) -> oldValue));
    }

    /**
     * @MethodName ensureProblemsExist
     * @Param problemIds
     * @Description 校验题目都存在
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public void ensureProblemsExist(List<Long> problemIds) {
        List<Long> normalizedIds = normalizeProblemIds(problemIds);
        if (normalizedIds.isEmpty()) {
            return;
        }
        Map<Long, ProblemBasicInfoDto> problemMap = queryProblemMap(normalizedIds);
        for (Long problemId : normalizedIds) {
            if (!problemMap.containsKey(problemId)) {
                throw new BizException(ResultCode.PROBLEM_NOT_FOUND, "题目不存在: " + problemId);
            }
        }
    }

    /**
     * @MethodName normalizeProblemIds
     * @Param problemIds
     * @Description 标准化题目 id 列表
     * @Return @return {@link List }<{@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public List<Long> normalizeProblemIds(List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> idSet = new LinkedHashSet<>();
        for (Long problemId : problemIds) {
            if (problemId != null && problemId > 0) {
                idSet.add(problemId);
            }
        }
        return idSet.stream().toList();
    }
}
