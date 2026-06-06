package com.hnieacm.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.entity.Problem;
import com.hnieacm.problem.mapper.ProblemMapper;
import com.hnieacm.problem.service.ProblemFileStorageService;
import com.hnieacm.problem.service.ProblemResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 题目资源业务服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemResourceServiceImpl implements ProblemResourceService {

    private static final int DEFAULT_DATA_VERSION = 1;

    private final ProblemMapper problemMapper;
    private final ProblemFileStorageService fileStorageService;

    @Override
    public String uploadImage(Long problemId, MultipartFile file) {
        requireProblem(problemId);
        return fileStorageService.saveImage(problemId, file);
    }

    @Override
    public void deleteImage(Long problemId, String filename) {
        requireProblem(problemId);
        fileStorageService.deleteImage(problemId, filename);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceTestdata(Long problemId, MultipartFile file) {
        requireProblem(problemId);
        fileStorageService.validateTestdata(file);
        int updated = problemMapper.update(null, new LambdaUpdateWrapper<Problem>()
                .eq(Problem::getId, problemId)
                .setSql("data_version = IFNULL(data_version, 1) + 1"));
        if (updated <= 0) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "更新测试数据版本失败");
        }
        fileStorageService.replaceTestdata(problemId, file);
    }

    @Override
    public TestdataDownloadDecision prepareTestdataDownload(Long problemId, Integer version) {
        Problem problem = requireProblem(problemId);
        Integer dataVersion = problem.getDataVersion() == null ? DEFAULT_DATA_VERSION : problem.getDataVersion();
        if (version != null && version.equals(dataVersion)) {
            log.info("Problem testdata not modified, problemId: {}, version: {}", problemId, version);
            return new TestdataDownloadDecision(true, dataVersion);
        }
        if (!fileStorageService.hasAvailableTestdata(problemId)) {
            throw new BizException(ResultCode.NOT_FOUND, "测试数据不存在");
        }
        log.info("Problem testdata download required, problemId: {}, clientVersion: {}, latestVersion: {}",
                problemId, version, dataVersion);
        return new TestdataDownloadDecision(false, dataVersion);
    }

    @Override
    public void writeTestdataZip(Long problemId, OutputStream outputStream) {
        requireProblem(problemId);
        fileStorageService.writeTestdataZip(problemId, outputStream);
    }

    private Problem requireProblem(Long problemId) {
        if (problemId == null || problemId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "problemId 不合法");
        }
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new BizException(ResultCode.PROBLEM_NOT_FOUND, "题目不存在");
        }
        return problem;
    }

}
