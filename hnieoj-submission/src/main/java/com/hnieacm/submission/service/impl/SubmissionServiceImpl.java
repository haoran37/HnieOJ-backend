package com.hnieacm.submission.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.common.util.PageParamUtils;
import com.hnieacm.submission.constant.ProblemAuthConstant;
import com.hnieacm.submission.constant.SubmissionConstant;
import com.hnieacm.submission.constant.SubmissionStatusConstant;
import com.hnieacm.submission.dto.ProblemBasicDto;
import com.hnieacm.submission.dto.SubmissionListQueryRequest;
import com.hnieacm.submission.dto.SubmitCodeRequest;
import com.hnieacm.submission.dto.UserDetailDto;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeCase;
import com.hnieacm.submission.feign.ProblemInternalFeignClient;
import com.hnieacm.submission.feign.UserProfileFeignClient;
import com.hnieacm.submission.mapper.JudgeCaseMapper;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.properties.SubmissionProperties;
import com.hnieacm.submission.service.JudgeNodeAccessService;
import com.hnieacm.submission.service.JudgeTaskMessagePublisher;
import com.hnieacm.submission.service.SubmissionService;
import com.hnieacm.submission.vo.SubmissionCaseVo;
import com.hnieacm.submission.vo.SubmissionDetailVo;
import com.hnieacm.submission.vo.SubmissionListItemVo;
import com.hnieacm.submission.vo.SubmitCodeVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 提交服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private static final String DEFAULT_JUDGE_MODE = "default";
    private static final String SPJ_JUDGE_MODE = "spj";
    private static final String INTERACTIVE_JUDGE_MODE = "interactive";

    private final JudgeMapper judgeMapper;
    private final JudgeCaseMapper judgeCaseMapper;
    private final ProblemInternalFeignClient problemInternalFeignClient;
    private final UserProfileFeignClient userProfileFeignClient;
    private final JudgeNodeAccessService judgeNodeAccessService;
    private final JudgeTaskMessagePublisher judgeTaskMessagePublisher;
    private final SubmissionProperties submissionProperties;

    /**
     * @MethodName submit
     * @Param request
     * @Param file
     * @Description 提交
     * @Return @return {@link SubmitCodeVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitCodeVo submit(SubmitCodeRequest request, MultipartFile file) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }

        String problemCode = StrUtil.trim(request.getProblemCode());
        if (StrUtil.isBlank(problemCode)) {
            throw new BizException(ResultCode.BAD_REQUEST, "problemCode不能为空");
        }

        String language = StrUtil.trim(request.getLanguage());
        if (StrUtil.isBlank(language)) {
            throw new BizException(ResultCode.BAD_REQUEST, "language不能为空");
        }

        String code = StrUtil.trimToNull(request.getCode());
        if (code != null) {
            validateCodeSize(code);
        }
        if (StrUtil.isBlank(code) && (file == null || file.isEmpty())) {
            throw new BizException(ResultCode.BAD_REQUEST, "code和file不能同时为空");
        }

        if (StrUtil.isBlank(code) && file != null && !file.isEmpty()) {
            validateCodeFileSize(file);
            try {
                code = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new BizException(ResultCode.BAD_REQUEST, "读取代码文件失败");
            }
            code = StrUtil.trimToNull(code);
            if (StrUtil.isBlank(code)) {
                throw new BizException(ResultCode.BAD_REQUEST, "代码文件内容不能为空");
            }
            validateCodeSize(code);
        }

        String uid = StpUtil.getLoginIdAsString();

        ProblemBasicDto problem = queryProblemBasic(problemCode);
        ensureProblemSubmitAllowed(problem);

        String username = queryUsername(uid);

        long cid = parseContestId(request.getContestId());
        String submitId = generateUuid32();

        Judge judge = new Judge();
        judge.setSubmitId(submitId);
        judge.setProblemId(problem.getId());
        judge.setProblemCode(problem.getProblemCode());
        judge.setUid(uid);
        judge.setUsername(username);
        judge.setLanguage(language);
        judge.setCode(code);
        judge.setStatus(SubmissionStatusConstant.PENDING);
        judge.setJudgeTaskId(generateUuid32());
        judge.setCid(cid);
        judge.setTotalCase(0);
        judge.setJudgedCase(0);
        judge.setCurrentCase(0);
        judge.setCpid(SubmissionConstant.DEFAULT_CPID);
        judge.setTid(SubmissionConstant.DEFAULT_TID);
        judge.setHid(SubmissionConstant.DEFAULT_HID);
        judge.setIsManual(false);
        judge.setIp(resolveClientIp());

        judgeMapper.insert(judge);

        judgeTaskMessagePublisher.publishAfterCommit(judge, problem);
        log.info("Submission created, submitId={}, problemCode={}, uid={}, language={}", submitId, problemCode, uid, language);

        return new SubmitCodeVo(submitId);
    }

    /**
     * @MethodName rejudgeSubmission
     * @Param submissionId
     * @Description 重判单个提交
     * @Return @return {@link SubmitCodeVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitCodeVo rejudgeSubmission(String submissionId) {
        String normalizedSubmissionId = trimToNull(submissionId);
        if (normalizedSubmissionId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "submissionId 不能为空");
        }

        Judge judge = queryJudge(normalizedSubmissionId);
        ensureSubmissionRejudgeAllowed(judge);
        ProblemBasicDto problem = queryProblemBasic(judge.getProblemCode());
        ensureProblemHasTestdata(problem);

        String judgeTaskId = generateUuid32();
        // 重判必须生成新的任务 ID，避免旧判题任务的延迟回调污染新一轮结果。
        judgeMapper.update(null, new LambdaUpdateWrapper<Judge>()
                .eq(Judge::getId, judge.getId())
                .notIn(Judge::getStatus, SubmissionStatusConstant.PENDING,
                        SubmissionStatusConstant.COMPILING, SubmissionStatusConstant.RUNNING)
                .set(Judge::getJudgeTaskId, judgeTaskId)
                .set(Judge::getStatus, SubmissionStatusConstant.PENDING)
                .set(Judge::getErrorMessage, null)
                .set(Judge::getDiagnosticMessage, null)
                .set(Judge::getTime, null)
                .set(Judge::getMemory, null)
                .set(Judge::getScore, null)
                .set(Judge::getTotalCase, 0)
                .set(Judge::getJudgedCase, 0)
                .set(Judge::getCurrentCase, 0)
                .set(Judge::getJudger, null)
                .set(Judge::getIsManual, true));
        Judge updatedJudge = judgeMapper.selectById(judge.getId());
        if (updatedJudge == null || !judgeTaskId.equals(updatedJudge.getJudgeTaskId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "提交正在判题中，暂不能重判");
        }
        judgeCaseMapper.delete(new LambdaQueryWrapper<JudgeCase>()
                .eq(JudgeCase::getSubmitId, judge.getId()));

        judge.setJudgeTaskId(judgeTaskId);
        judge.setStatus(SubmissionStatusConstant.PENDING);
        judge.setTotalCase(0);
        judge.setJudgedCase(0);
        judge.setCurrentCase(0);
        judgeTaskMessagePublisher.publishAfterCommit(judge, problem);
        log.info("Submission rejudge task published, submissionId={}, judgeId={}, operator={}",
                normalizedSubmissionId, judge.getId(), StpUtil.getLoginIdAsString());
        return new SubmitCodeVo(normalizedSubmissionId);
    }

    /**
     * @MethodName listSubmissions
     * @Param request
     * @Description 分页查询提交记录
     * @Return @return {@link PageVo }<{@link SubmissionListItemVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public PageVo<SubmissionListItemVo> listSubmissions(SubmissionListQueryRequest request) {
        int page = normalizePage(request == null ? null : request.getPage());
        int pageSize = normalizePageSize(request == null ? null : request.getPageSize());
        boolean admin = isAdmin();
        String currentUid = StpUtil.getLoginIdAsString();

        LambdaQueryWrapper<Judge> wrapper = new LambdaQueryWrapper<Judge>()
                .orderByDesc(Judge::getGmtCreate)
                .orderByDesc(Judge::getId);

        if (admin) {
            String queryUid = trimToNull(request == null ? null : request.getUid());
            if (queryUid != null) {
                wrapper.eq(Judge::getUid, queryUid);
            }
        } else {
            wrapper.eq(Judge::getUid, currentUid);
        }

        String problemCode = trimToNull(request == null ? null : request.getProblemCode());
        if (problemCode != null) {
            wrapper.eq(Judge::getProblemCode, problemCode);
        }

        String language = trimToNull(request == null ? null : request.getLanguage());
        if (language != null) {
            wrapper.eq(Judge::getLanguage, language);
        }

        Integer status = request == null ? null : request.getStatus();
        if (status != null) {
            wrapper.eq(Judge::getStatus, status);
        }

        Long contestId = request == null ? null : request.getContestId();
        if (contestId != null) {
            if (contestId < 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "contestId 不能小于 0");
            }
            wrapper.eq(Judge::getCid, contestId);
        }

        Page<Judge> pageResult = judgeMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<Judge> records = pageResult.getRecords();
        if (records == null || records.isEmpty()) {
            return new PageVo<>(Collections.emptyList(), pageResult.getTotal());
        }
        return new PageVo<>(records.stream().map(this::toListItemVo).toList(), pageResult.getTotal());
    }

    /**
     * @MethodName getSubmissionDetail
     * @Param submissionId
     * @Description 查询提交详情
     * @Return @return {@link SubmissionDetailVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public SubmissionDetailVo getSubmissionDetail(String submissionId) {
        Judge judge = queryAccessibleJudge(submissionId);
        return toDetailVo(judge);
    }

    /**
     * @MethodName listSubmissionCases
     * @Param submissionId
     * @Description 查询提交的测试点结果
     * @Return @return {@link List }<{@link SubmissionCaseVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public List<SubmissionCaseVo> listSubmissionCases(String submissionId) {
        Judge judge = queryAccessibleJudge(submissionId);
        boolean admin = isAdmin();

        List<JudgeCase> cases = judgeCaseMapper.selectList(new LambdaQueryWrapper<JudgeCase>()
                .eq(JudgeCase::getSubmitId, judge.getId())
                .orderByAsc(JudgeCase::getId));
        if (cases == null || cases.isEmpty()) {
            return Collections.emptyList();
        }

        return cases.stream()
                .sorted(Comparator.comparingInt(this::caseOrder).thenComparing(JudgeCase::getId))
                .map(item -> toCaseVo(item, admin))
                .toList();
    }

    /**
     * @MethodName queryProblemBasic
     * @Param problemCode
     * @Description 查询题目基本信息
     * @Return @return {@link ProblemBasicDto }
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    private ProblemBasicDto queryProblemBasic(String problemCode) {
        Result<ProblemBasicDto> result;
        try {
            result = problemInternalFeignClient.getProblemBasic(problemCode);
        } catch (Exception e) {
            log.warn("Query problem basic failed, problemCode: {}", problemCode, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "查询题目信息失败");
        }

        if (result == null || result.getCode() != ResultCode.SUCCESS || result.getData() == null) {
            throw new BizException(ResultCode.PROBLEM_NOT_FOUND, "题目不存在");
        }
        return result.getData();
    }

    /**
     * @MethodName queryAccessibleJudge
     * @Param submissionId
     * @Description 查询当前用户可访问的提交记录
     * @Return @return {@link Judge }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private Judge queryAccessibleJudge(String submissionId) {
        String normalizedSubmissionId = trimToNull(submissionId);
        if (normalizedSubmissionId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "submissionId 不能为空");
        }

        Judge judge = queryJudge(normalizedSubmissionId);

        String currentUid = StpUtil.getLoginIdAsString();
        if (!currentUid.equals(judge.getUid()) && !isAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问该提交记录");
        }
        return judge;
    }

    /**
     * @MethodName queryJudge
     * @Param submissionId
     * @Description 根据提交展示 ID 查询提交记录
     * @Return @return {@link Judge }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private Judge queryJudge(String submissionId) {
        Judge judge = judgeMapper.selectOne(new LambdaQueryWrapper<Judge>()
                .eq(Judge::getSubmitId, submissionId)
                .last("limit 1"));
        if (judge == null) {
            throw new BizException(ResultCode.SUBMISSION_NOT_FOUND, "提交记录不存在");
        }
        return judge;
    }

    /**
     * @MethodName toListItemVo
     * @Param judge
     * @Description 转换提交列表展示对象
     * @Return @return {@link SubmissionListItemVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private SubmissionListItemVo toListItemVo(Judge judge) {
        SubmissionListItemVo vo = new SubmissionListItemVo();
        vo.setSubmissionId(judge.getSubmitId());
        vo.setProblemCode(judge.getProblemCode());
        vo.setUid(judge.getUid());
        vo.setUsername(judge.getUsername());
        vo.setLanguage(judge.getLanguage());
        vo.setStatus(judge.getStatus());
        vo.setStatusText(SubmissionStatusConstant.toText(judge.getStatus()));
        vo.setTime(judge.getTime());
        vo.setMemory(judge.getMemory());
        vo.setScore(judge.getScore());
        vo.setContestId(judge.getCid());
        vo.setTotalCase(defaultZero(judge.getTotalCase()));
        vo.setJudgedCase(defaultZero(judge.getJudgedCase()));
        vo.setCurrentCase(defaultZero(judge.getCurrentCase()));
        vo.setGmtCreate(judge.getGmtCreate());
        return vo;
    }

    /**
     * @MethodName toDetailVo
     * @Param judge
     * @Description 转换提交详情展示对象
     * @Return @return {@link SubmissionDetailVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private SubmissionDetailVo toDetailVo(Judge judge) {
        SubmissionDetailVo vo = new SubmissionDetailVo();
        vo.setSubmissionId(judge.getSubmitId());
        vo.setProblemCode(judge.getProblemCode());
        vo.setUid(judge.getUid());
        vo.setUsername(judge.getUsername());
        vo.setLanguage(judge.getLanguage());
        vo.setStatus(judge.getStatus());
        vo.setStatusText(SubmissionStatusConstant.toText(judge.getStatus()));
        vo.setTime(judge.getTime());
        vo.setMemory(judge.getMemory());
        vo.setScore(judge.getScore());
        vo.setContestId(judge.getCid());
        vo.setTotalCase(defaultZero(judge.getTotalCase()));
        vo.setJudgedCase(defaultZero(judge.getJudgedCase()));
        vo.setCurrentCase(defaultZero(judge.getCurrentCase()));
        vo.setErrorMessage(judge.getErrorMessage());
        if (includeDiagnosticData()) {
            vo.setDiagnosticMessage(judge.getDiagnosticMessage());
        }
        vo.setJudger(judge.getJudger());
        vo.setCode(judge.getCode());
        vo.setGmtCreate(judge.getGmtCreate());
        vo.setGmtModified(judge.getGmtModified());
        return vo;
    }

    /**
     * @MethodName toCaseVo
     * @Param judgeCase
     * @Param includeSensitiveData
     * @Description 转换测试点结果展示对象
     * @Return @return {@link SubmissionCaseVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private SubmissionCaseVo toCaseVo(JudgeCase judgeCase, boolean includeSensitiveData) {
        SubmissionCaseVo vo = new SubmissionCaseVo();
        vo.setCaseId(judgeCase.getCaseId());
        vo.setStatus(judgeCase.getStatus());
        vo.setStatusText(SubmissionStatusConstant.toText(judgeCase.getStatus()));
        vo.setTime(judgeCase.getTime());
        vo.setMemory(judgeCase.getMemory());
        vo.setScore(judgeCase.getScore());
        if (includeSensitiveData) {
            vo.setInputData(judgeCase.getInputData());
            vo.setOutputData(judgeCase.getOutputData());
            vo.setUserOutput(judgeCase.getUserOutput());
        }
        vo.setGmtCreate(judgeCase.getGmtCreate());
        vo.setGmtModified(judgeCase.getGmtModified());
        return vo;
    }

    /**
     * @MethodName ensureProblemSubmitAllowed
     * @Param problem
     * @Description 确保允许提交题目
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    private void ensureProblemSubmitAllowed(ProblemBasicDto problem) {
        if (problem.getAuth() == null) {
            throw new BizException(ResultCode.PROBLEM_NOT_FOUND, "题目不存在");
        }

        // 当前版本下，私有/比赛题目仅允许题目管理员提交。
        if (problem.getAuth() != ProblemAuthConstant.PUBLIC && !StpUtil.hasPermission(PermissionConstant.PROBLEM_UPDATE)) {
            throw new BizException(ResultCode.FORBIDDEN, "该题目当前不可提交");
        }
        if (!Boolean.TRUE.equals(problem.getHasTestdata()) || defaultZero(problem.getTestdataCaseCount()) <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "题目测试数据未配置，暂不能提交");
        }
        ensureJudgeModeSupported(problem);
    }

    /**
     * @MethodName ensureProblemHasTestdata
     * @Param problem
     * @Description 校验题目是否已配置测试数据
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void ensureProblemHasTestdata(ProblemBasicDto problem) {
        if (problem.getAuth() == null) {
            throw new BizException(ResultCode.PROBLEM_NOT_FOUND, "题目不存在");
        }
        if (!Boolean.TRUE.equals(problem.getHasTestdata()) || defaultZero(problem.getTestdataCaseCount()) <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "题目测试数据未配置，暂不能重判");
        }
        ensureJudgeModeSupported(problem);
    }

    /**
     * @MethodName ensureJudgeModeSupported
     * @Param problem
     * @Description 校验当前判题节点能力是否支持题目判题模式
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void ensureJudgeModeSupported(ProblemBasicDto problem) {
        String judgeMode = StrUtil.blankToDefault(problem.getJudgeMode(), DEFAULT_JUDGE_MODE);
        List<String> supportedModes = submissionProperties.getSupportedJudgeModes();
        if (supportedModes == null || supportedModes.isEmpty()) {
            supportedModes = List.of(DEFAULT_JUDGE_MODE);
        }
        boolean supported = supportedModes.stream()
                .map(item -> StrUtil.blankToDefault(item, DEFAULT_JUDGE_MODE))
                .anyMatch(item -> item.equalsIgnoreCase(judgeMode));
        if (!supported) {
            throw new BizException(ResultCode.BAD_REQUEST, "当前判题节点暂不支持该题目的判题模式: " + judgeMode);
        }
        ensureJudgeModeContract(problem, judgeMode);
        if (!judgeNodeAccessService.hasActiveNodeForMode(judgeMode)) {
            throw new BizException(ResultCode.BAD_REQUEST, "当前没有可用判题节点支持该判题模式: " + judgeMode);
        }
    }

    /**
     * @MethodName ensureJudgeModeContract
     * @Param problem
     * @Param judgeMode
     * @Description 校验特殊判题模式所需合约字段
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void ensureJudgeModeContract(ProblemBasicDto problem, String judgeMode) {
        if (DEFAULT_JUDGE_MODE.equalsIgnoreCase(judgeMode)) {
            return;
        }
        if (SPJ_JUDGE_MODE.equalsIgnoreCase(judgeMode)) {
            if (StrUtil.isBlank(problem.getSpjCode()) || StrUtil.isBlank(problem.getSpjLanguage())) {
                throw new BizException(ResultCode.BAD_REQUEST, "SPJ 题目必须配置 checker 源码和语言");
            }
            return;
        }
        if (INTERACTIVE_JUDGE_MODE.equalsIgnoreCase(judgeMode)) {
            if (StrUtil.isBlank(problem.getInteractorCode()) || StrUtil.isBlank(problem.getInteractorLanguage())) {
                throw new BizException(ResultCode.BAD_REQUEST, "交互题必须配置 interactor 源码和语言");
            }
            return;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "不支持的判题模式: " + judgeMode);
    }

    /**
     * @MethodName ensureSubmissionRejudgeAllowed
     * @Param judge
     * @Description 校验提交是否允许重判
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void ensureSubmissionRejudgeAllowed(Judge judge) {
        if (SubmissionStatusConstant.isJudging(judge.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "提交正在判题中，暂不能重判");
        }
    }

    /**
     * @MethodName queryUsername
     * @Param uid
     * @Description 查询用户名
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    private String queryUsername(String uid) {
        if (StrUtil.isBlank(uid)) {
            return "-";
        }
        try {
            Result<UserDetailDto> result = userProfileFeignClient.getUserDetail(uid);
            if (result != null && result.getCode() == ResultCode.SUCCESS && result.getData() != null) {
                String username = StrUtil.trimToNull(result.getData().getUsername());
                if (StrUtil.isNotBlank(username)) {
                    return username;
                }
            }
        } catch (Exception e) {
            log.debug("Query username failed, uid: {}", uid, e);
        }
        // 降级返回 uid，避免因远程调用失败影响提交流程。
        return uid;
    }

    /**
     * @MethodName isAdmin
     * @Description 判断当前用户是否为管理员
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private boolean isAdmin() {
        return StpUtil.hasRole(RoleConstant.ADMIN) || StpUtil.hasRole(RoleConstant.ROOT);
    }

    /**
     * @MethodName includeDiagnosticData
     * @Description 判断当前用户是否允许查看诊断信息
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private boolean includeDiagnosticData() {
        return isAdmin() || StpUtil.hasRole(RoleConstant.TEACHER)
                || StpUtil.hasPermission(PermissionConstant.PROBLEM_UPDATE);
    }

    /**
     * @MethodName normalizePage
     * @Param page
     * @Description 规范化页码
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private int normalizePage(Integer page) {
        return PageParamUtils.normalizePage(page);
    }

    /**
     * @MethodName normalizePageSize
     * @Param pageSize
     * @Description 规范化每页数量
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private int normalizePageSize(Integer pageSize) {
        return PageParamUtils.normalizePageSize(pageSize);
    }

    /**
     * @MethodName trimToNull
     * @Param value
     * @Description 去除字符串首尾空白，空字符串返回 null
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private String trimToNull(String value) {
        return StrUtil.trimToNull(value);
    }

    /**
     * @MethodName defaultZero
     * @Param value
     * @Description 空值转为 0
     * @Return @return {@link Integer }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private Integer defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * @MethodName validateCodeFileSize
     * @Param file
     * @Description 校验上传代码文件大小
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void validateCodeFileSize(MultipartFile file) {
        long maxCodeBytes = maxCodeBytes();
        if (file.getSize() > maxCodeBytes) {
            throw new BizException(ResultCode.BAD_REQUEST, "代码文件不能超过 " + maxCodeBytes + " 字节");
        }
    }

    /**
     * @MethodName validateCodeSize
     * @Param code
     * @Description 校验提交源码大小
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void validateCodeSize(String code) {
        long maxCodeBytes = maxCodeBytes();
        int codeBytes = code.getBytes(StandardCharsets.UTF_8).length;
        if (codeBytes > maxCodeBytes) {
            throw new BizException(ResultCode.BAD_REQUEST, "代码长度不能超过 " + maxCodeBytes + " 字节");
        }
    }

    /**
     * @MethodName maxCodeBytes
     * @Description 获取提交源码最大字节数
     * @Return @return long
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private long maxCodeBytes() {
        Integer maxCodeBytes = submissionProperties.getMaxCodeBytes();
        if (maxCodeBytes == null || maxCodeBytes <= 0) {
            return 65536L;
        }
        return maxCodeBytes.longValue();
    }

    /**
     * @MethodName caseOrder
     * @Param judgeCase
     * @Description 解析测试点排序值
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private int caseOrder(JudgeCase judgeCase) {
        String caseId = trimToNull(judgeCase.getCaseId());
        if (caseId == null) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(caseId);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * @MethodName parseContestId
     * @Param contestId
     * @Description 解析比赛id
     * @Return @return long
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    private long parseContestId(String contestId) {
        String normalized = StrUtil.trimToNull(contestId);
        if (normalized == null) {
            return SubmissionConstant.DEFAULT_CID;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "contestId格式不正确");
        }
    }

    /**
     * @MethodName generateUuid32
     *
     * @Description 生成uuid32
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    private String generateUuid32() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * @MethodName resolveClientIp
     *
     * @Description 解析客户端ip
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    private String resolveClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            return attrs.getRequest().getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
