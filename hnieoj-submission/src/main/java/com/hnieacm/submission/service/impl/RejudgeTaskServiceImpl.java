package com.hnieacm.submission.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.submission.constant.RejudgeTaskStatusConstant;
import com.hnieacm.submission.constant.SubmissionStatusConstant;
import com.hnieacm.submission.dto.CreateRejudgeTaskRequest;
import com.hnieacm.submission.dto.ProblemBasicDto;
import com.hnieacm.submission.dto.RejudgeTaskQueryRequest;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.entity.JudgeCase;
import com.hnieacm.submission.entity.RejudgeTask;
import com.hnieacm.submission.entity.RejudgeTaskDetail;
import com.hnieacm.submission.feign.ProblemInternalFeignClient;
import com.hnieacm.submission.mapper.JudgeCaseMapper;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.mapper.RejudgeTaskDetailMapper;
import com.hnieacm.submission.mapper.RejudgeTaskMapper;
import com.hnieacm.submission.properties.SubmissionProperties;
import com.hnieacm.submission.service.JudgeNodeAccessService;
import com.hnieacm.submission.service.JudgeTaskMessagePublisher;
import com.hnieacm.submission.service.RejudgeTaskService;
import com.hnieacm.submission.vo.RejudgeTaskDetailVo;
import com.hnieacm.submission.vo.RejudgeTaskVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 重判任务服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RejudgeTaskServiceImpl implements RejudgeTaskService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final long DEFAULT_LEASE_SECONDS = 300L;
    private static final int DEFAULT_LEASE_RENEW_EVERY = 10;
    private static final int WORKER_ID_UUID_LENGTH = 8;
    private static final int MAX_WORKER_ID_LENGTH = 128;
    private static final String DEFAULT_JUDGE_MODE = "default";
    private static final String SPJ_JUDGE_MODE = "spj";
    private static final String INTERACTIVE_JUDGE_MODE = "interactive";
    private static final Set<String> STATUS_SET = Set.of(
            RejudgeTaskStatusConstant.PENDING,
            RejudgeTaskStatusConstant.PROCESSING,
            RejudgeTaskStatusConstant.FINISHED,
            RejudgeTaskStatusConstant.FAILED
    );

    private final RejudgeTaskMapper rejudgeTaskMapper;
    private final RejudgeTaskDetailMapper rejudgeTaskDetailMapper;
    private final JudgeMapper judgeMapper;
    private final JudgeCaseMapper judgeCaseMapper;
    private final ProblemInternalFeignClient problemInternalFeignClient;
    private final JudgeNodeAccessService judgeNodeAccessService;
    private final JudgeTaskMessagePublisher judgeTaskMessagePublisher;
    private final SubmissionProperties submissionProperties;
    private final TransactionTemplate transactionTemplate;
    private final String workerId = buildWorkerId();

    /**
     * @MethodName create
     * @Param request
     * @Description 创建批量重判任务并固化重判明细
     * @Return @return {@link RejudgeTaskVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RejudgeTaskVo create(CreateRejudgeTaskRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        String problemCode = StrUtil.trimToNull(request.getProblemCode());
        if (problemCode == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "problemCode 不能为空");
        }
        if (request.getRangeStart() != null && request.getRangeEnd() != null
                && request.getRangeStart().isAfter(request.getRangeEnd())) {
            throw new BizException(ResultCode.BAD_REQUEST, "rangeStart 不能晚于 rangeEnd");
        }
        ProblemBasicDto problem = queryProblemBasic(problemCode);
        ensureProblemHasTestdata(problem);

        List<Judge> targetJudges = judgeMapper.selectList(buildJudgeRangeWrapper(problem.getId(), request.getContestId(),
                request.getRangeStart(), request.getRangeEnd(), null)
                .orderByAsc(Judge::getId));
        long totalCount = targetJudges.size();
        RejudgeTask task = new RejudgeTask();
        task.setProblemId(problem.getId());
        task.setProblemCode(problem.getProblemCode());
        task.setContestId(request.getContestId());
        task.setRangeStart(request.getRangeStart());
        task.setRangeEnd(request.getRangeEnd());
        task.setStatus(totalCount == 0 ? RejudgeTaskStatusConstant.FINISHED : RejudgeTaskStatusConstant.PENDING);
        task.setTotalCount(toInteger(totalCount));
        task.setProcessedCount(0);
        task.setFailedCount(0);
        task.setLastJudgeId(0L);
        task.setAdminId(StpUtil.getLoginIdAsString());
        rejudgeTaskMapper.insert(task);
        saveTaskDetails(task, targetJudges);
        return toVo(task);
    }

    /**
     * @MethodName list
     * @Param request
     * @Description 分页查询批量重判任务
     * @Return @return {@link PageVo }<{@link RejudgeTaskVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public PageVo<RejudgeTaskVo> list(RejudgeTaskQueryRequest request) {
        int page = normalizePage(request == null ? null : request.getPage());
        int pageSize = normalizePageSize(request == null ? null : request.getPageSize());
        LambdaQueryWrapper<RejudgeTask> wrapper = new LambdaQueryWrapper<RejudgeTask>()
                .orderByDesc(RejudgeTask::getGmtCreate)
                .orderByDesc(RejudgeTask::getId);

        String status = normalizeStatus(request == null ? null : request.getStatus());
        if (status != null) {
            wrapper.eq(RejudgeTask::getStatus, status);
        }
        String problemCode = StrUtil.trimToNull(request == null ? null : request.getProblemCode());
        if (problemCode != null) {
            wrapper.eq(RejudgeTask::getProblemCode, problemCode);
        }

        Page<RejudgeTask> pageResult = rejudgeTaskMapper.selectPage(new Page<>(page, pageSize), wrapper);
        if (pageResult.getRecords() == null || pageResult.getRecords().isEmpty()) {
            return new PageVo<>(Collections.emptyList(), pageResult.getTotal());
        }
        return new PageVo<>(pageResult.getRecords().stream().map(this::toVo).toList(), pageResult.getTotal());
    }

    /**
     * @MethodName details
     * @Param taskId
     * @Description 查询批量重判任务明细和前后状态对比
     * @Return @return {@link List }<{@link RejudgeTaskDetailVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public List<RejudgeTaskDetailVo> details(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "taskId must be positive");
        }
        RejudgeTask task = rejudgeTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ResultCode.NOT_FOUND, "Rejudge task not found");
        }
        List<RejudgeTaskDetail> details = rejudgeTaskDetailMapper.selectList(
                new LambdaQueryWrapper<RejudgeTaskDetail>()
                        .eq(RejudgeTaskDetail::getTaskId, taskId)
                        .orderByAsc(RejudgeTaskDetail::getJudgeId)
                        .orderByAsc(RejudgeTaskDetail::getId));
        if (details.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> judgeIds = details.stream().map(RejudgeTaskDetail::getJudgeId).toList();
        List<Judge> judges = judgeMapper.selectList(new LambdaQueryWrapper<Judge>()
                .in(Judge::getId, judgeIds));
        return details.stream().map(detail -> toDetailVo(detail, findJudge(judges, detail.getJudgeId()))).toList();
    }

    /**
     * @MethodName processPendingTasks
     * @Description 定时领取并推进批量重判任务
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Scheduled(fixedDelayString = "${hnieoj.submission.rejudge-task.scan-interval-ms:10000}")
    public void processPendingTasks() {
        LocalDateTime now = LocalDateTime.now();
        RejudgeTask task = rejudgeTaskMapper.selectOne(new LambdaQueryWrapper<RejudgeTask>()
                .and(wrapper -> wrapper
                        .eq(RejudgeTask::getStatus, RejudgeTaskStatusConstant.PENDING)
                        .or(item -> item.eq(RejudgeTask::getStatus, RejudgeTaskStatusConstant.PROCESSING)
                                .and(lock -> lock.isNull(RejudgeTask::getLockUntil)
                                        .or()
                                        .le(RejudgeTask::getLockUntil, now))))
                .orderByAsc(RejudgeTask::getId)
                .last("limit 1"));
        if (task == null) {
            return;
        }
        RejudgeTask claimedTask = claimTask(task, now);
        if (claimedTask == null) {
            return;
        }
        processTask(claimedTask);
    }

    /**
     * @MethodName claimTask
     * @Param task
     * @Param now
     * @Description 通过租约抢占一个可处理的重判任务
     * @Return @return {@link RejudgeTask }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private RejudgeTask claimTask(RejudgeTask task, LocalDateTime now) {
        LocalDateTime lockUntil = now.plusSeconds(leaseSeconds());
        LambdaUpdateWrapper<RejudgeTask> wrapper = new LambdaUpdateWrapper<RejudgeTask>()
                .eq(RejudgeTask::getId, task.getId())
                .set(RejudgeTask::getStatus, RejudgeTaskStatusConstant.PROCESSING)
                .set(RejudgeTask::getLockedBy, workerId)
                .set(RejudgeTask::getLockUntil, lockUntil);
        if (RejudgeTaskStatusConstant.PENDING.equals(task.getStatus())) {
            wrapper.eq(RejudgeTask::getStatus, RejudgeTaskStatusConstant.PENDING);
        } else {
            wrapper.eq(RejudgeTask::getStatus, RejudgeTaskStatusConstant.PROCESSING)
                    .and(lock -> lock.isNull(RejudgeTask::getLockUntil)
                            .or()
                            .le(RejudgeTask::getLockUntil, now));
        }
        int updated = rejudgeTaskMapper.update(null, wrapper);
        if (updated <= 0) {
            log.info("Rejudge task lease skipped, taskId: {}, workerId: {}", task.getId(), workerId);
            return null;
        }
        RejudgeTask claimedTask = rejudgeTaskMapper.selectById(task.getId());
        if (claimedTask == null) {
            log.warn("Rejudge task lease claimed but task disappeared, taskId: {}, workerId: {}",
                    task.getId(), workerId);
        }
        return claimedTask;
    }

    /**
     * @MethodName processTask
     * @Param task
     * @Description 按重判明细批量重置提交并投递判题任务
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void processTask(RejudgeTask task) {
        ProblemBasicDto problem;
        try {
            problem = queryProblemBasic(task.getProblemCode());
            ensureProblemHasTestdata(problem);
        } catch (Exception e) {
            markTaskFailed(task.getId(), e.getMessage());
            return;
        }

        List<RejudgeTaskDetail> details = rejudgeTaskDetailMapper.selectList(
                new LambdaQueryWrapper<RejudgeTaskDetail>()
                        .eq(RejudgeTaskDetail::getTaskId, task.getId())
                        .gt(task.getLastJudgeId() != null && task.getLastJudgeId() > 0,
                                RejudgeTaskDetail::getJudgeId, task.getLastJudgeId())
                        .orderByAsc(RejudgeTaskDetail::getJudgeId)
                        .orderByAsc(RejudgeTaskDetail::getId)
                        .last("limit " + batchSize()));
        if (details.isEmpty()) {
            markTaskFinished(task.getId());
            return;
        }
        List<Long> judgeIds = details.stream().map(RejudgeTaskDetail::getJudgeId).toList();
        List<Judge> judges = judgeMapper.selectList(new LambdaQueryWrapper<Judge>()
                .in(Judge::getId, judgeIds)
                .orderByAsc(Judge::getId)
                .last("limit " + batchSize()));
        if (judges.isEmpty()) {
            advanceTask(task.getId(), details.get(details.size() - 1).getJudgeId(), 0, details.size(),
                    "Submission records not found");
            return;
        }
        long lastJudgeId = task.getLastJudgeId() == null ? 0L : task.getLastJudgeId();
        int processed = 0;
        int failed = 0;
        String lastError = null;
        for (RejudgeTaskDetail detail : details) {
            if (processed % leaseRenewEvery() == 0 && !renewTaskLease(task.getId())) {
                log.warn("Rejudge task processing stopped because lease renew failed, taskId: {}, workerId: {}",
                        task.getId(), workerId);
                return;
            }
            lastJudgeId = detail.getJudgeId();
            Judge judge = findJudge(judges, detail.getJudgeId());
            if (judge == null) {
                failed++;
                lastError = "Submission record not found";
                continue;
            }
            try {
                rejudge(judge, problem, task.getId());
                processed++;
            } catch (BizException e) {
                failed++;
                lastError = e.getMessage();
                log.warn("Rejudge submission skipped, taskId: {}, submissionId: {}, reason: {}",
                        task.getId(), judge.getSubmitId(), e.getMessage());
            } catch (Exception e) {
                failed++;
                lastError = e.getMessage();
                log.warn("Rejudge submission failed, taskId: {}, submissionId: {}", task.getId(), judge.getSubmitId(), e);
            }
        }
        if (!renewTaskLease(task.getId())) {
            log.warn("Rejudge task advance skipped because lease renew failed, taskId: {}, workerId: {}",
                    task.getId(), workerId);
            return;
        }
        advanceTask(task.getId(), lastJudgeId, processed, failed, lastError);
    }

    /**
     * @MethodName buildJudgeRangeWrapper
     * @Param problemId
     * @Param contestId
     * @Param rangeStart
     * @Param rangeEnd
     * @Param lastJudgeId
     * @Description 构造可重判提交范围查询条件
     * @Return @return {@link LambdaQueryWrapper }<{@link Judge }>
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private LambdaQueryWrapper<Judge> buildJudgeRangeWrapper(Long problemId, Long contestId,
                                                             LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                             Long lastJudgeId) {
        LambdaQueryWrapper<Judge> wrapper = buildJudgeTaskScopeWrapper(problemId, contestId, rangeStart, rangeEnd,
                lastJudgeId)
                .notIn(Judge::getStatus, SubmissionStatusConstant.PENDING,
                        SubmissionStatusConstant.COMPILING, SubmissionStatusConstant.RUNNING);
        return wrapper;
    }

    /**
     * @MethodName buildJudgeTaskScopeWrapper
     * @Param problemId
     * @Param contestId
     * @Param rangeStart
     * @Param rangeEnd
     * @Param lastJudgeId
     * @Description 构造重判任务范围内提交查询条件
     * @Return @return {@link LambdaQueryWrapper }<{@link Judge }>
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private LambdaQueryWrapper<Judge> buildJudgeTaskScopeWrapper(Long problemId, Long contestId,
                                                                 LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                                 Long lastJudgeId) {
        LambdaQueryWrapper<Judge> wrapper = new LambdaQueryWrapper<Judge>()
                .eq(Judge::getProblemId, problemId);
        if (contestId != null) {
            wrapper.eq(Judge::getCid, contestId);
        }
        if (rangeStart != null) {
            wrapper.ge(Judge::getGmtCreate, rangeStart);
        }
        if (rangeEnd != null) {
            wrapper.le(Judge::getGmtCreate, rangeEnd);
        }
        if (lastJudgeId != null && lastJudgeId > 0) {
            wrapper.gt(Judge::getId, lastJudgeId);
        }
        return wrapper;
    }

    /**
     * @MethodName rejudge
     * @Param judge
     * @Param problem
     * @Param taskId
     * @Description 重置单条提交并投递新的判题任务
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void rejudge(Judge judge, ProblemBasicDto problem, Long taskId) {
        transactionTemplate.executeWithoutResult(status -> {
            String judgeTaskId = UUID.randomUUID().toString().replace("-", "");
            int updated = judgeMapper.update(null, new LambdaUpdateWrapper<Judge>()
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
            if (updated <= 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "提交正在判题中，暂不能重判");
            }
            judgeCaseMapper.delete(new LambdaQueryWrapper<JudgeCase>()
                    .eq(JudgeCase::getSubmitId, judge.getId()));
            judge.setJudgeTaskId(judgeTaskId);
            judge.setStatus(SubmissionStatusConstant.PENDING);
            judge.setTotalCase(0);
            judge.setJudgedCase(0);
            judge.setCurrentCase(0);
            bindTaskDetailToJudgeTask(taskId, judge.getId(), judgeTaskId);
            judgeTaskMessagePublisher.publishAfterCommit(judge, problem);
        });
    }

    /**
     * @MethodName queryProblemBasic
     * @Param problemCode
     * @Description 查询题目判题所需基础信息
     * @Return @return {@link ProblemBasicDto }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
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
     * @MethodName ensureProblemHasTestdata
     * @Param problem
     * @Description 校验题目是否已配置测试数据
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void ensureProblemHasTestdata(ProblemBasicDto problem) {
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
     * @Description 校验特殊判题模式必需的任务合约字段
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
     * @MethodName advanceTask
     * @Param taskId
     * @Param lastJudgeId
     * @Param processed
     * @Param failed
     * @Param lastError
     * @Description 推进重判任务处理进度
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void advanceTask(Long taskId, Long lastJudgeId, int processed, int failed, String lastError) {
        int updated = rejudgeTaskMapper.update(null, new LambdaUpdateWrapper<RejudgeTask>()
                .eq(RejudgeTask::getId, taskId)
                .eq(RejudgeTask::getLockedBy, workerId)
                .eq(RejudgeTask::getStatus, RejudgeTaskStatusConstant.PROCESSING)
                .gt(RejudgeTask::getLockUntil, LocalDateTime.now())
                .set(RejudgeTask::getLastJudgeId, lastJudgeId)
                .setSql("processed_count = processed_count + " + processed)
                .setSql("failed_count = failed_count + " + failed)
                .set(RejudgeTask::getLastError, StrUtil.trimToNull(lastError))
                .set(RejudgeTask::getLockedBy, null)
                .set(RejudgeTask::getLockUntil, null));
        if (updated <= 0) {
            log.warn("Advance rejudge task skipped because lease changed, taskId: {}, workerId: {}", taskId, workerId);
        }
    }

    /**
     * @MethodName markTaskFinished
     * @Param taskId
     * @Description 标记重判任务完成
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void markTaskFinished(Long taskId) {
        int updated = rejudgeTaskMapper.update(null, new LambdaUpdateWrapper<RejudgeTask>()
                .eq(RejudgeTask::getId, taskId)
                .eq(RejudgeTask::getLockedBy, workerId)
                .eq(RejudgeTask::getStatus, RejudgeTaskStatusConstant.PROCESSING)
                .gt(RejudgeTask::getLockUntil, LocalDateTime.now())
                .set(RejudgeTask::getStatus, RejudgeTaskStatusConstant.FINISHED)
                .set(RejudgeTask::getLockedBy, null)
                .set(RejudgeTask::getLockUntil, null));
        if (updated <= 0) {
            log.warn("Finish rejudge task skipped because lease changed, taskId: {}, workerId: {}", taskId, workerId);
        }
    }

    /**
     * @MethodName markTaskFailed
     * @Param taskId
     * @Param errorMessage
     * @Description 标记重判任务失败
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void markTaskFailed(Long taskId, String errorMessage) {
        int updated = rejudgeTaskMapper.update(null, new LambdaUpdateWrapper<RejudgeTask>()
                .eq(RejudgeTask::getId, taskId)
                .eq(RejudgeTask::getLockedBy, workerId)
                .eq(RejudgeTask::getStatus, RejudgeTaskStatusConstant.PROCESSING)
                .gt(RejudgeTask::getLockUntil, LocalDateTime.now())
                .set(RejudgeTask::getStatus, RejudgeTaskStatusConstant.FAILED)
                .set(RejudgeTask::getLastError, StrUtil.trimToNull(errorMessage))
                .set(RejudgeTask::getLockedBy, null)
                .set(RejudgeTask::getLockUntil, null));
        if (updated <= 0) {
            log.warn("Fail rejudge task skipped because lease changed, taskId: {}, workerId: {}", taskId, workerId);
        }
    }

    /**
     * @MethodName renewTaskLease
     * @Param taskId
     * @Description 续约当前实例持有的重判任务租约
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private boolean renewTaskLease(Long taskId) {
        int updated = rejudgeTaskMapper.update(null, new LambdaUpdateWrapper<RejudgeTask>()
                .eq(RejudgeTask::getId, taskId)
                .eq(RejudgeTask::getStatus, RejudgeTaskStatusConstant.PROCESSING)
                .eq(RejudgeTask::getLockedBy, workerId)
                .gt(RejudgeTask::getLockUntil, LocalDateTime.now())
                .set(RejudgeTask::getLockUntil, LocalDateTime.now().plusSeconds(leaseSeconds())));
        return updated > 0;
    }

    /**
     * @MethodName toVo
     * @Param task
     * @Description 转换重判任务展示对象
     * @Return @return {@link RejudgeTaskVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private RejudgeTaskVo toVo(RejudgeTask task) {
        RejudgeTaskVo vo = new RejudgeTaskVo();
        vo.setId(task.getId());
        vo.setProblemId(task.getProblemId());
        vo.setProblemCode(task.getProblemCode());
        vo.setContestId(task.getContestId());
        vo.setRangeStart(task.getRangeStart());
        vo.setRangeEnd(task.getRangeEnd());
        vo.setStatus(task.getStatus());
        vo.setTotalCount(defaultZero(task.getTotalCount()));
        vo.setProcessedCount(defaultZero(task.getProcessedCount()));
        vo.setFailedCount(defaultZero(task.getFailedCount()));
        vo.setLastJudgeId(task.getLastJudgeId());
        vo.setLastError(task.getLastError());
        vo.setLockedBy(task.getLockedBy());
        vo.setLockUntil(task.getLockUntil());
        vo.setAdminId(task.getAdminId());
        vo.setGmtCreate(task.getGmtCreate());
        vo.setGmtModified(task.getGmtModified());
        return vo;
    }

    /**
     * @MethodName saveTaskDetails
     * @Param task
     * @Param judges
     * @Description 保存重判任务明细并记录重判前状态
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void saveTaskDetails(RejudgeTask task, List<Judge> judges) {
        if (judges == null || judges.isEmpty()) {
            return;
        }
        for (Judge judge : judges) {
            RejudgeTaskDetail detail = new RejudgeTaskDetail();
            detail.setTaskId(task.getId());
            detail.setJudgeId(judge.getId());
            detail.setSubmitId(judge.getSubmitId());
            detail.setProblemId(judge.getProblemId());
            detail.setProblemCode(judge.getProblemCode());
            detail.setUid(judge.getUid());
            detail.setUsername(judge.getUsername());
            detail.setLanguage(judge.getLanguage());
            detail.setOriginalStatus(judge.getStatus());
            detail.setOriginalScore(judge.getScore());
            detail.setOriginalTime(judge.getTime());
            detail.setOriginalMemory(judge.getMemory());
            detail.setSubmitTime(judge.getGmtCreate());
            rejudgeTaskDetailMapper.insert(detail);
        }
    }

    /**
     * @MethodName bindTaskDetailToJudgeTask
     * @Param taskId
     * @Param judgeId
     * @Param judgeTaskId
     * @Description 将重判明细绑定到本次判题任务 ID
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void bindTaskDetailToJudgeTask(Long taskId, Long judgeId, String judgeTaskId) {
        if (taskId == null || judgeId == null || StrUtil.isBlank(judgeTaskId)) {
            return;
        }
        rejudgeTaskDetailMapper.update(null, new LambdaUpdateWrapper<RejudgeTaskDetail>()
                .eq(RejudgeTaskDetail::getTaskId, taskId)
                .eq(RejudgeTaskDetail::getJudgeId, judgeId)
                .set(RejudgeTaskDetail::getJudgeTaskId, judgeTaskId)
                .set(RejudgeTaskDetail::getFinalStatus, null)
                .set(RejudgeTaskDetail::getFinalScore, null)
                .set(RejudgeTaskDetail::getFinalTime, null)
                .set(RejudgeTaskDetail::getFinalMemory, null)
                .set(RejudgeTaskDetail::getFinishedTime, null));
    }

    /**
     * @MethodName findJudge
     * @Param judges
     * @Param judgeId
     * @Description 从批量查询结果中查找指定提交
     * @Return @return {@link Judge }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private Judge findJudge(List<Judge> judges, Long judgeId) {
        if (judges == null || judgeId == null) {
            return null;
        }
        return judges.stream()
                .filter(judge -> judgeId.equals(judge.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * @MethodName toDetailVo
     * @Param detail
     * @Param judge
     * @Description 转换重判明细前后状态展示对象
     * @Return @return {@link RejudgeTaskDetailVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private RejudgeTaskDetailVo toDetailVo(RejudgeTaskDetail detail, Judge judge) {
        Integer currentStatus = detail.getFinalStatus() == null
                ? (judge == null ? null : judge.getStatus())
                : detail.getFinalStatus();
        Integer currentScore = detail.getFinalScore() == null
                ? (judge == null ? null : judge.getScore())
                : detail.getFinalScore();
        Integer currentTime = detail.getFinalTime() == null
                ? (judge == null ? null : judge.getTime())
                : detail.getFinalTime();
        Integer currentMemory = detail.getFinalMemory() == null
                ? (judge == null ? null : judge.getMemory())
                : detail.getFinalMemory();
        RejudgeTaskDetailVo vo = new RejudgeTaskDetailVo();
        vo.setRunId(detail.getSubmitId());
        vo.setSubmissionId(detail.getSubmitId());
        vo.setUid(detail.getUid());
        vo.setUsername(detail.getUsername());
        vo.setOriginalStatus(SubmissionStatusConstant.toText(detail.getOriginalStatus()));
        vo.setCurrentStatus(SubmissionStatusConstant.toText(currentStatus));
        vo.setStatus(currentStatus);
        vo.setOriginalStatusCode(detail.getOriginalStatus());
        vo.setCurrentStatusCode(currentStatus);
        vo.setLanguage(detail.getLanguage());
        vo.setScore(currentScore);
        vo.setOriginalScore(detail.getOriginalScore());
        vo.setCurrentScore(currentScore);
        vo.setTime(currentTime);
        vo.setOriginalTime(detail.getOriginalTime());
        vo.setCurrentTime(currentTime);
        vo.setMemory(currentMemory);
        vo.setOriginalMemory(detail.getOriginalMemory());
        vo.setCurrentMemory(currentMemory);
        vo.setSubmitTime(detail.getSubmitTime());
        vo.setFinishedTime(detail.getFinishedTime());
        return vo;
    }

    /**
     * @MethodName normalizeStatus
     * @Param status
     * @Description 规范化并校验重判任务状态
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private String normalizeStatus(String status) {
        String normalized = StrUtil.trimToNull(status);
        if (normalized == null) {
            return null;
        }
        if (!STATUS_SET.contains(normalized)) {
            throw new BizException(ResultCode.BAD_REQUEST, "status 不合法");
        }
        return normalized;
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
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 必须大于 0");
        }
        return page;
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
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "pageSize 必须大于 0");
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ResultCode.BAD_REQUEST, "pageSize 不能大于 " + MAX_PAGE_SIZE);
        }
        return pageSize;
    }

    /**
     * @MethodName batchSize
     * @Description 获取重判任务每轮处理批次大小
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private int batchSize() {
        Integer value = submissionProperties.getRejudgeTask().getBatchSize();
        return value == null || value <= 0 ? DEFAULT_BATCH_SIZE : value;
    }

    /**
     * @MethodName leaseSeconds
     * @Description 获取重判任务租约秒数
     * @Return @return long
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private long leaseSeconds() {
        Long value = submissionProperties.getRejudgeTask().getLeaseSeconds();
        return value == null || value <= 0 ? DEFAULT_LEASE_SECONDS : value;
    }

    /**
     * @MethodName leaseRenewEvery
     * @Description 获取重判任务续约间隔条数
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private int leaseRenewEvery() {
        Integer value = submissionProperties.getRejudgeTask().getLeaseRenewEvery();
        return value == null || value <= 0 ? DEFAULT_LEASE_RENEW_EVERY : value;
    }

    /**
     * @MethodName buildWorkerId
     * @Description 构造当前服务实例的重判任务 workerId
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private String buildWorkerId() {
        String hostName;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostName = "unknown-host";
        }
        String processName = ManagementFactory.getRuntimeMXBean().getName();
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, WORKER_ID_UUID_LENGTH);
        String value = hostName + ":" + processName + ":" + uuid;
        return value.length() <= MAX_WORKER_ID_LENGTH ? value : value.substring(0, MAX_WORKER_ID_LENGTH);
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
     * @MethodName toInteger
     * @Param value
     * @Description long 转 int，超出范围时按最大 int 处理
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private int toInteger(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
