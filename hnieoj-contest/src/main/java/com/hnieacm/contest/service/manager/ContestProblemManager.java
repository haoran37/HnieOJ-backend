package com.hnieacm.contest.service.manager;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.contest.feign.ProblemInternalFeignClient;
import com.hnieacm.contest.feign.dto.ProblemBasicInfoDto;
import com.hnieacm.contest.feign.dto.ProblemBatchQueryRequest;
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
 * @Date: 2026/02/27
 * @Description: 题目远程查询管理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContestProblemManager {

    private final ProblemInternalFeignClient problemInternalFeignClient;

    /**
     * 批量查询题目 id -> title 映射
     */
    public Map<Long, String> queryProblemTitleMap(List<Long> problemIds) {
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
                        problem -> problem.getTitle() == null ? "" : problem.getTitle(),
                        (oldValue, newValue) -> oldValue));
    }

    /**
     * 校验题目都存在，不存在时抛业务异常
     */
    public void ensureProblemsExist(List<Long> problemIds) {
        List<Long> normalizedIds = normalizeProblemIds(problemIds);
        if (normalizedIds.isEmpty()) {
            return;
        }
        Map<Long, String> titleMap = queryProblemTitleMap(normalizedIds);
        for (Long problemId : normalizedIds) {
            if (!titleMap.containsKey(problemId)) {
                throw new BizException(ResultCode.PROBLEM_NOT_FOUND, "题目不存在: " + problemId);
            }
        }
    }

    /**
     * 标准化 problemId
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
