package com.hnieacm.submission.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.submission.constant.ProblemAuthConstant;
import com.hnieacm.submission.constant.SubmissionConstant;
import com.hnieacm.submission.constant.SubmissionStatusConstant;
import com.hnieacm.submission.dto.ProblemBasicDto;
import com.hnieacm.submission.dto.SubmitCodeRequest;
import com.hnieacm.submission.dto.UserDetailDto;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.feign.ProblemInternalFeignClient;
import com.hnieacm.submission.feign.UserProfileFeignClient;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.service.SubmissionService;
import com.hnieacm.submission.vo.SubmitCodeVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
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

    private final JudgeMapper judgeMapper;
    private final ProblemInternalFeignClient problemInternalFeignClient;
    private final UserProfileFeignClient userProfileFeignClient;

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
        if (StrUtil.isBlank(code) && (file == null || file.isEmpty())) {
            throw new BizException(ResultCode.BAD_REQUEST, "code和file不能同时为空");
        }

        if (StrUtil.isBlank(code) && file != null && !file.isEmpty()) {
            try {
                code = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new BizException(ResultCode.BAD_REQUEST, "读取代码文件失败");
            }
            code = StrUtil.trimToNull(code);
            if (StrUtil.isBlank(code)) {
                throw new BizException(ResultCode.BAD_REQUEST, "代码文件内容不能为空");
            }
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
        judge.setCid(cid);
        judge.setCpid(SubmissionConstant.DEFAULT_CPID);
        judge.setTid(SubmissionConstant.DEFAULT_TID);
        judge.setHid(SubmissionConstant.DEFAULT_HID);
        judge.setIsManual(false);
        judge.setIp(resolveClientIp());

        judgeMapper.insert(judge);

        // TODO: 这里后续通过 RocketMQ 下发判题任务，当前仅记录提交日志。
        log.info("Submission created, submitId={}, problemCode={}, uid={}, language={}", submitId, problemCode, uid, language);

        return new SubmitCodeVo(submitId);
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
