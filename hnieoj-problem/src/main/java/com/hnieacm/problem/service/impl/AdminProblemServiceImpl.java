package com.hnieacm.problem.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.constant.ProblemAuthConstant;
import com.hnieacm.problem.dto.AddProblemRequest;
import com.hnieacm.problem.dto.ProblemRequest;
import com.hnieacm.problem.dto.UpdateProblemAuthRequest;
import com.hnieacm.problem.dto.UpdateProblemRequest;
import com.hnieacm.problem.entity.Problem;
import com.hnieacm.problem.mapper.ProblemMapper;
import com.hnieacm.problem.mapper.ProblemTagMapper;
import com.hnieacm.problem.mapper.TagMapper;
import com.hnieacm.problem.service.AdminProblemService;
import com.hnieacm.problem.vo.AdminProblemListVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 题目管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProblemServiceImpl implements AdminProblemService {

    private final ProblemMapper problemMapper;
    private final TagMapper tagMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ObjectMapper objectMapper;

    /**
     * @MethodName listProblems
     * @Param page
     * @Param pageSize
     * @Param keyword
     * @Param auth
     * @Description 题目列表
     * @Return @return {@link PageVo }<{@link AdminProblemListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    @Override
    public PageVo<AdminProblemListVo> listProblems(int page, int pageSize, String keyword, Integer auth) {
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page和pageSize必须大于0");
        }

        String normalizedKeyword = StrUtil.trimToNull(keyword);

        LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<Problem>()
                .select(
                        Problem::getId,
                        Problem::getProblemCode,
                        Problem::getTitle,
                        Problem::getAuthor,
                        Problem::getAuth,
                        Problem::getType,
                        Problem::getDifficulty,
                        Problem::getSubmissionCount,
                        Problem::getAcceptedCount,
                        Problem::getScorePercentage,
                        Problem::getGmtCreate,
                        Problem::getGmtModified
                );
        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(Problem::getTitle, normalizedKeyword)
                    .or()
                    .like(Problem::getProblemCode, normalizedKeyword));
        }
        if (auth != null) {
            wrapper.eq(Problem::getAuth, auth);
        }
        ProblemServiceSupport.ProblemPageResult pageResult = ProblemServiceSupport.queryProblemPageWithTags(
                problemMapper, problemTagMapper, tagMapper, wrapper, page, pageSize
        );
        List<Problem> records = pageResult.records();
        Map<Long, List<String>> tagsMap = pageResult.tagsMap();

        List<AdminProblemListVo> list = records.stream().map(problem -> {
            AdminProblemListVo vo = new AdminProblemListVo();
            vo.setId(problem.getId());
            vo.setProblemCode(problem.getProblemCode());
            vo.setTitle(problem.getTitle());
            vo.setAuthor(problem.getAuthor());
            vo.setAuth(problem.getAuth());
            vo.setType(problem.getType());
            vo.setDifficulty(problem.getDifficulty());
            vo.setTags(tagsMap.getOrDefault(problem.getId(), Collections.emptyList()));
            vo.setSubmissionCount(problem.getSubmissionCount());
            vo.setAcceptedCount(problem.getAcceptedCount());
            vo.setScorePercentage(problem.getScorePercentage());
            vo.setCreateTime(problem.getGmtCreate());
            vo.setUpdateTime(problem.getGmtModified());
            return vo;
        }).toList();

        return new PageVo<>(list, pageResult.total());
    }

    /**
     * @MethodName addProblem
     * @Param request
     * @Description 添加题目
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addProblem(AddProblemRequest request) {
        if (request == null || request.getProblem() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "problem不能为空");
        }

        ProblemRequest pr = request.getProblem();
        String problemCode = ProblemServiceSupport.requireProblemCode(pr.getProblemCode());

        Long exists = problemMapper.selectCount(
                new LambdaQueryWrapper<Problem>().eq(Problem::getProblemCode, problemCode)
        );
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "problemCode已存在");
        }

        Problem entity = buildProblemEntityForSave(pr, request.getJudgeMode());
        entity.setProblemCode(problemCode);

        if (StrUtil.isBlank(entity.getAuthor())) {
            entity.setAuthor(StpUtil.getLoginIdAsString());
        }
        entity.setModifiedUser(StpUtil.getLoginIdAsString());

        problemMapper.insert(entity);
        saveProblemTags(entity.getId(), request.getTags());

        ProblemServiceSupport.logUnpersistedLanguages(log, entity.getProblemCode(), request.getLanguages());
    }

    /**
     * @MethodName updateProblem
     * @Param request
     * @Description 更新题目
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProblem(UpdateProblemRequest request) {
        if (request == null || request.getProblem() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "problem不能为空");
        }

        ProblemRequest pr = request.getProblem();
        if (pr.getId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "id is required");
        }

        Problem existed = problemMapper.selectById(pr.getId());
        if (existed == null) {
            throw new BizException(ResultCode.PROBLEM_NOT_FOUND, "题目不存在");
        }

        String normalizedProblemCode = ProblemServiceSupport.requireProblemCode(pr.getProblemCode());
        if (!Objects.equals(existed.getProblemCode(), normalizedProblemCode)) {
            Long count = problemMapper.selectCount(
                    new LambdaQueryWrapper<Problem>().eq(Problem::getProblemCode, normalizedProblemCode)
            );
            if (count != null && count > 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "problemCode已存在");
            }
        }

        Problem entity = buildProblemEntityForSave(pr, request.getJudgeMode());
        entity.setId(pr.getId());
        entity.setProblemCode(normalizedProblemCode);
        entity.setModifiedUser(StpUtil.getLoginIdAsString());

        problemMapper.updateById(entity);
        saveProblemTags(entity.getId(), request.getTags());

        ProblemServiceSupport.logUnpersistedLanguages(log, entity.getProblemCode(), request.getLanguages());
    }

    /**
     * @MethodName deleteProblem
     * @Param id
     * @Description 删除题目
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProblem(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "id不能为空");
        }

        Problem existed = problemMapper.selectById(id);
        if (existed == null) {
            throw new BizException(ResultCode.PROBLEM_NOT_FOUND, "题目不存在");
        }

        ProblemServiceSupport.deleteProblemTagsByProblemId(problemTagMapper, id);
        problemMapper.deleteById(id);
    }

    /**
     * @MethodName updateProblemAuth
     * @Param request
     * @Description 更新题目权限
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProblemAuth(UpdateProblemAuthRequest request) {
        if (request == null || request.getPid() == null || request.getAuth() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "pid和auth不能为空");
        }

        if (request.getAuth() != ProblemAuthConstant.PUBLIC && request.getAuth() != ProblemAuthConstant.PRIVATE) {
            throw new BizException(ResultCode.BAD_REQUEST, "auth只允许为1（公开）或2（私有）");
        }

        Problem existed = problemMapper.selectById(request.getPid());
        if (existed == null) {
            throw new BizException(ResultCode.PROBLEM_NOT_FOUND, "题目不存在");
        }

        problemMapper.update(
                null,
                new LambdaUpdateWrapper<Problem>()
                        .eq(Problem::getId, request.getPid())
                        .set(Problem::getAuth, request.getAuth())
                        .set(Problem::getModifiedUser, StpUtil.getLoginIdAsString())
        );
    }

    /**
     * @MethodName buildProblemEntityForSave
     * @Param pr
     * @Param judgeModeFromRequest
     * @Description 构建题目实体以进行保存
     * @Return @return {@link Problem }
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    private Problem buildProblemEntityForSave(ProblemRequest pr, String judgeModeFromRequest) {
        Problem entity = new Problem();
        entity.setProblemCode(pr.getProblemCode());
        entity.setTitle(StrUtil.trimToNull(pr.getTitle()));
        entity.setAuthor(StrUtil.trimToNull(pr.getAuthor()));
        entity.setType(pr.getType());

        String judgeMode = StrUtil.trimToNull(pr.getJudgeMode());
        if (StrUtil.isNotBlank(judgeModeFromRequest)) {
            judgeMode = StrUtil.trimToNull(judgeModeFromRequest);
        }
        entity.setJudgeMode(judgeMode);

        entity.setTimeLimit(pr.getTimeLimit());
        entity.setMemoryLimit(pr.getMemoryLimit());
        entity.setStackLimit(pr.getStackLimit());
        entity.setDescription(pr.getDescription());
        entity.setInput(pr.getInput());
        entity.setOutput(pr.getOutput());
        entity.setHint(pr.getHint());
        entity.setDifficulty(pr.getDifficulty());
        entity.setAuth(pr.getAuth());
        entity.setIoScore(pr.getIoScore());
        entity.setIsRemote(pr.getIsRemote());
        entity.setSource(pr.getSource());
        entity.setSpjCode(pr.getSpjCode());
        entity.setSpjLanguage(pr.getSpjLanguage());
        entity.setIsRemoveEndBlank(pr.getIsRemoveEndBlank());
        entity.setOpenCaseResult(pr.getOpenCaseResult());

        if (pr.getExamples() != null) {
            try {
                entity.setExamples(objectMapper.writeValueAsString(pr.getExamples()));
            } catch (Exception e) {
                throw new BizException(ResultCode.BAD_REQUEST, "examples格式不正确");
            }
        } else {
            entity.setExamples(null);
        }
        return entity;
    }

    /**
     * @MethodName saveProblemTags
     * @Param problemDbId
     * @Param tags
     * @Description 保存题目标签
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    private void saveProblemTags(Long problemDbId, List<String> tags) {
        ProblemServiceSupport.replaceProblemTags(problemTagMapper, tagMapper, problemDbId, tags);
    }
}
