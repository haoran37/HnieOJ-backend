package com.hnieacm.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.problem.dto.ProblemBasicDto;
import com.hnieacm.problem.entity.Problem;
import com.hnieacm.problem.mapper.ProblemMapper;
import com.hnieacm.problem.service.InternalProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 内部题目服务实现
 */
@Service
@RequiredArgsConstructor
public class InternalProblemServiceImpl implements InternalProblemService {

    private final ProblemMapper problemMapper;

    /**
     * @MethodName getProblemBasicByProblemCode
     * @Param problemCode
     * @Description 通过 ProblemCode 获取题目基本信息
     * @Return @return {@link ProblemBasicDto }
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    @Override
    public ProblemBasicDto getProblemBasicByProblemCode(String problemCode) {
        Problem problem = ProblemServiceSupport.queryProblemByCode(problemMapper, problemCode);

        ProblemBasicDto dto = new ProblemBasicDto();
        dto.setId(problem.getId());
        dto.setProblemCode(problem.getProblemCode());
        dto.setTitle(problem.getTitle());
        dto.setAuth(problem.getAuth());
        dto.setType(problem.getType());
        fillJudgeConfig(dto, problem);
        return dto;
    }

    @Override
    public List<ProblemBasicDto> getProblemBasicsByIds(List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> normalizedIds = problemIds.stream()
                .filter(Objects::nonNull)
                .filter(problemId -> problemId > 0)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Problem> problems = problemMapper.selectList(new LambdaQueryWrapper<Problem>()
                .select(Problem::getId, Problem::getProblemCode, Problem::getTitle, Problem::getAuth, Problem::getType)
                .in(Problem::getId, normalizedIds));
        if (problems == null || problems.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Problem> problemMap = problems.stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity(), (oldValue, newValue) -> oldValue));
        return normalizedIds.stream()
                .map(problemMap::get)
                .filter(Objects::nonNull)
                .map(this::toBasicDto)
                .toList();
    }

    private ProblemBasicDto toBasicDto(Problem problem) {
        ProblemBasicDto dto = new ProblemBasicDto();
        dto.setId(problem.getId());
        dto.setProblemCode(problem.getProblemCode());
        dto.setTitle(problem.getTitle());
        dto.setAuth(problem.getAuth());
        dto.setType(problem.getType());
        fillJudgeConfig(dto, problem);
        return dto;
    }

    private void fillJudgeConfig(ProblemBasicDto dto, Problem problem) {
        dto.setJudgeMode(problem.getJudgeMode());
        dto.setTimeLimit(problem.getTimeLimit());
        dto.setMemoryLimit(problem.getMemoryLimit());
        dto.setStackLimit(problem.getStackLimit());
        dto.setIoScore(problem.getIoScore());
        dto.setIsRemoveEndBlank(problem.getIsRemoveEndBlank());
        dto.setDataVersion(problem.getDataVersion());
    }
}
