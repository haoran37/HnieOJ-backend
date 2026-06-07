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
import com.hnieacm.submission.feign.ProblemInternalFeignClient;
import com.hnieacm.submission.mapper.JudgeCaseMapper;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.mapper.RejudgeTaskMapper;
import com.hnieacm.submission.properties.SubmissionProperties;
import com.hnieacm.submission.service.JudgeTaskMessagePublisher;
import com.hnieacm.submission.service.RejudgeTaskService;
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
    private static final int WORKER_ID_UUID_LENGTH = 8;
    private static final int MAX_WORKER_ID_LENGTH = 128;
    private static final Set<String> STATUS_SET = Set.of(
            RejudgeTaskStatusConstant.PENDING,
            RejudgeTaskStatusConstant.PROCESSING,
            RejudgeTaskStatusConstant.FINISHED,
            RejudgeTaskStatusConstant.FAILED
    );

    private final RejudgeTaskMapper rejudgeTaskMapper;
    private final JudgeMapper judgeMapper;
    private final JudgeCaseMapper judgeCaseMapper;
    private final ProblemInternalFeignClient problemInternalFeignClient;
    private final JudgeTaskMessagePublisher judgeTaskMessagePublisher;
    private final SubmissionProperties submissionProperties;
    private final TransactionTemplate transactionTemplate;
    private final String workerId = buildWorkerId();

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

        long totalCount = judgeMapper.selectCount(buildJudgeRangeWrapper(problem.getId(), request.getContestId(),
                request.getRangeStart(), request.getRangeEnd(), null));
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
        return toVo(task);
    }

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

    private void processTask(RejudgeTask task) {
        ProblemBasicDto problem;
        try {
            problem = queryProblemBasic(task.getProblemCode());
            ensureProblemHasTestdata(problem);
        } catch (Exception e) {
            markTaskFailed(task.getId(), e.getMessage());
            return;
        }

        List<Judge> judges = judgeMapper.selectList(buildJudgeRangeWrapper(task.getProblemId(), task.getContestId(),
                task.getRangeStart(), task.getRangeEnd(), task.getLastJudgeId())
                .orderByAsc(Judge::getId)
                .last("limit " + batchSize()));
        if (judges.isEmpty()) {
            markTaskFinished(task.getId());
            return;
        }
        long lastJudgeId = task.getLastJudgeId() == null ? 0L : task.getLastJudgeId();
        int processed = 0;
        int failed = 0;
        String lastError = null;
        for (Judge judge : judges) {
            lastJudgeId = judge.getId();
            try {
                rejudge(judge, problem);
                processed++;
            } catch (Exception e) {
                failed++;
                lastError = e.getMessage();
                log.warn("Rejudge submission failed, taskId: {}, submissionId: {}", task.getId(), judge.getSubmitId(), e);
            }
        }
        advanceTask(task.getId(), lastJudgeId, processed, failed, lastError);
    }

    private LambdaQueryWrapper<Judge> buildJudgeRangeWrapper(Long problemId, Long contestId,
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

    private void rejudge(Judge judge, ProblemBasicDto problem) {
        transactionTemplate.executeWithoutResult(status -> {
            String judgeTaskId = UUID.randomUUID().toString().replace("-", "");
            judgeMapper.update(null, new LambdaUpdateWrapper<Judge>()
                    .eq(Judge::getId, judge.getId())
                    .set(Judge::getJudgeTaskId, judgeTaskId)
                    .set(Judge::getStatus, SubmissionStatusConstant.PENDING)
                    .set(Judge::getErrorMessage, null)
                    .set(Judge::getTime, null)
                    .set(Judge::getMemory, null)
                    .set(Judge::getScore, null)
                    .set(Judge::getTotalCase, 0)
                    .set(Judge::getJudgedCase, 0)
                    .set(Judge::getCurrentCase, 0)
                    .set(Judge::getJudger, null)
                    .set(Judge::getIsManual, true));
            judgeCaseMapper.delete(new LambdaQueryWrapper<JudgeCase>()
                    .eq(JudgeCase::getSubmitId, judge.getId()));
            judge.setJudgeTaskId(judgeTaskId);
            judge.setStatus(SubmissionStatusConstant.PENDING);
            judge.setTotalCase(0);
            judge.setJudgedCase(0);
            judge.setCurrentCase(0);
            judgeTaskMessagePublisher.publishAfterCommit(judge, problem);
        });
    }

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

    private void ensureProblemHasTestdata(ProblemBasicDto problem) {
        if (!Boolean.TRUE.equals(problem.getHasTestdata()) || defaultZero(problem.getTestdataCaseCount()) <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "题目测试数据未配置，暂不能重判");
        }
    }

    private void advanceTask(Long taskId, Long lastJudgeId, int processed, int failed, String lastError) {
        int updated = rejudgeTaskMapper.update(null, new LambdaUpdateWrapper<RejudgeTask>()
                .eq(RejudgeTask::getId, taskId)
                .eq(RejudgeTask::getLockedBy, workerId)
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

    private void markTaskFinished(Long taskId) {
        int updated = rejudgeTaskMapper.update(null, new LambdaUpdateWrapper<RejudgeTask>()
                .eq(RejudgeTask::getId, taskId)
                .eq(RejudgeTask::getLockedBy, workerId)
                .set(RejudgeTask::getStatus, RejudgeTaskStatusConstant.FINISHED)
                .set(RejudgeTask::getLockedBy, null)
                .set(RejudgeTask::getLockUntil, null));
        if (updated <= 0) {
            log.warn("Finish rejudge task skipped because lease changed, taskId: {}, workerId: {}", taskId, workerId);
        }
    }

    private void markTaskFailed(Long taskId, String errorMessage) {
        int updated = rejudgeTaskMapper.update(null, new LambdaUpdateWrapper<RejudgeTask>()
                .eq(RejudgeTask::getId, taskId)
                .eq(RejudgeTask::getLockedBy, workerId)
                .set(RejudgeTask::getStatus, RejudgeTaskStatusConstant.FAILED)
                .set(RejudgeTask::getLastError, StrUtil.trimToNull(errorMessage))
                .set(RejudgeTask::getLockedBy, null)
                .set(RejudgeTask::getLockUntil, null));
        if (updated <= 0) {
            log.warn("Fail rejudge task skipped because lease changed, taskId: {}, workerId: {}", taskId, workerId);
        }
    }

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

    private int normalizePage(Integer page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 必须大于 0");
        }
        return page;
    }

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

    private int batchSize() {
        Integer value = submissionProperties.getRejudgeTask().getBatchSize();
        return value == null || value <= 0 ? DEFAULT_BATCH_SIZE : value;
    }

    private long leaseSeconds() {
        Long value = submissionProperties.getRejudgeTask().getLeaseSeconds();
        return value == null || value <= 0 ? DEFAULT_LEASE_SECONDS : value;
    }

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

    private Integer defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private int toInteger(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
