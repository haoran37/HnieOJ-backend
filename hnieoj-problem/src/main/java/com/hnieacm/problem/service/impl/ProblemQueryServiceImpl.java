package com.hnieacm.problem.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.constant.ProblemAuthConstant;
import com.hnieacm.problem.constant.ProblemTypeConstant;
import com.hnieacm.problem.entity.Problem;
import com.hnieacm.problem.entity.ProblemTag;
import com.hnieacm.problem.entity.Tag;
import com.hnieacm.problem.mapper.ProblemMapper;
import com.hnieacm.problem.mapper.ProblemTagMapper;
import com.hnieacm.problem.mapper.TagMapper;
import com.hnieacm.problem.service.ProblemQueryService;
import com.hnieacm.problem.vo.ProblemDetailVo;
import com.hnieacm.problem.vo.ProblemExampleVo;
import com.hnieacm.problem.vo.ProblemListVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 题目查询服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemQueryServiceImpl implements ProblemQueryService {

    private final ProblemMapper problemMapper;
    private final ProblemTagMapper problemTagMapper;
    private final TagMapper tagMapper;
    private final ObjectMapper objectMapper;

    /**
     * @MethodName listPublicProblems
     * @Param page
     * @Param pageSize
     * @Param keyword
     * @Param tags
     * @Param difficulty
     * @Description 公共题目列表
     * @Return @return {@link PageVo }<{@link ProblemListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    @Override
    public PageVo<ProblemListVo> listPublicProblems(int page, int pageSize, String keyword, List<String> tags, Integer difficulty) {
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page和pageSize必须大于0");
        }

        String normalizedKeyword = StrUtil.trimToNull(keyword);
        List<String> normalizedTags = ProblemServiceSupport.normalizeTagNames(tags);

        LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<Problem>()
                .select(
                        Problem::getId,
                        Problem::getProblemCode,
                        Problem::getTitle,
                        Problem::getDifficulty,
                        Problem::getSubmissionCount,
                        Problem::getAcceptedCount,
                        Problem::getScorePercentage
                )
                .eq(Problem::getAuth, ProblemAuthConstant.PUBLIC);

        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(Problem::getTitle, normalizedKeyword)
                    .or()
                    .like(Problem::getProblemCode, normalizedKeyword));
        }
        if (difficulty != null) {
            wrapper.eq(Problem::getDifficulty, difficulty);
        }

        if (!normalizedTags.isEmpty()) {
            List<Long> tagIds = tagMapper.selectList(new LambdaQueryWrapper<Tag>().in(Tag::getName, normalizedTags))
                    .stream()
                    .map(Tag::getId)
                    .filter(Objects::nonNull)
                    .toList();
            if (tagIds.isEmpty()) {
                return new PageVo<>(Collections.emptyList(), 0);
            }

            List<Long> problemIds = problemTagMapper.selectList(new LambdaQueryWrapper<ProblemTag>().in(ProblemTag::getTid, tagIds))
                    .stream()
                    .map(ProblemTag::getProblemId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (problemIds.isEmpty()) {
                return new PageVo<>(Collections.emptyList(), 0);
            }
            wrapper.in(Problem::getId, problemIds);
        }
        ProblemServiceSupport.ProblemPageResult pageResult = ProblemServiceSupport.queryProblemPageWithTags(
                problemMapper, problemTagMapper, tagMapper, wrapper, page, pageSize
        );
        List<Problem> records = pageResult.records();
        Map<Long, List<String>> tagsMap = pageResult.tagsMap();

        List<ProblemListVo> list = records.stream().map(problem -> {
            ProblemListVo vo = new ProblemListVo();
            vo.setId(problem.getId());
            vo.setProblemCode(problem.getProblemCode());
            vo.setTitle(problem.getTitle());
            vo.setDifficulty(problem.getDifficulty());
            vo.setTags(tagsMap.getOrDefault(problem.getId(), Collections.emptyList()));
            vo.setSubmissionCount(problem.getSubmissionCount());
            vo.setAcceptedCount(problem.getAcceptedCount());
            vo.setScorePercentage(problem.getScorePercentage());
            return vo;
        }).toList();

        return new PageVo<>(list, pageResult.total());
    }

    /**
     * @MethodName getProblemDetail
     * @Param problemCode
     * @Description 获取题目详细信息
     * @Return @return {@link ProblemDetailVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    @Override
    public ProblemDetailVo getProblemDetail(String problemCode) {
        Problem problem = ProblemServiceSupport.queryProblemByCode(problemMapper, problemCode);

        if (problem.getAuth() == null || problem.getAuth() != ProblemAuthConstant.PUBLIC) {
            if (!StpUtil.hasPermission(PermissionConstant.PROBLEM_UPDATE)) {
                throw new BizException(ResultCode.FORBIDDEN, "该题目当前不可访问");
            }
        }

        ProblemDetailVo vo = new ProblemDetailVo();
        vo.setId(problem.getId());
        vo.setProblemCode(problem.getProblemCode());
        vo.setTitle(problem.getTitle());
        vo.setAuthor(problem.getAuthor());
        vo.setType(problem.getType());
        vo.setJudgeMode(problem.getJudgeMode());
        vo.setTimeLimit(problem.getTimeLimit());
        vo.setMemoryLimit(problem.getMemoryLimit());
        vo.setStackLimit(problem.getStackLimit());
        vo.setDescription(problem.getDescription());
        vo.setInput(problem.getInput());
        vo.setOutput(problem.getOutput());
        vo.setExamples(parseExamples(problem.getExamples()));
        vo.setHint(problem.getHint());
        vo.setDifficulty(problem.getDifficulty());
        vo.setIoScore(problem.getType() != null && problem.getType() == ProblemTypeConstant.OI ? problem.getIoScore() : null);
        vo.setIsRemote(problem.getIsRemote());
        vo.setSource(problem.getSource());
        vo.setOpenCaseResult(problem.getOpenCaseResult());
        vo.setScorePercentage(problem.getScorePercentage());
        vo.setSubmissionCount(problem.getSubmissionCount());
        vo.setAcceptedCount(problem.getAcceptedCount());
        vo.setGmtCreate(problem.getGmtCreate());
        vo.setGmtModified(problem.getGmtModified());
        return vo;
    }

    /**
     * @MethodName parseExamples
     * @Param json
     * @Description 解析示例
     * @Return @return {@link List }<{@link ProblemExampleVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    private List<ProblemExampleVo> parseExamples(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("Parse problem examples failed, json: {}", json, e);
            return Collections.emptyList();
        }
    }
}
