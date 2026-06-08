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

    /**
     * @MethodName uploadImage
     * @Param problemId
     * @Param file
     * @Description 上传图片
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public String uploadImage(Long problemId, MultipartFile file) {
        requireProblem(problemId);
        return fileStorageService.saveImage(problemId, file);
    }

    /**
     * @MethodName deleteImage
     * @Param problemId
     * @Param filename
     * @Description 删除图像
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public void deleteImage(Long problemId, String filename) {
        requireProblem(problemId);
        fileStorageService.deleteImage(problemId, filename);
    }

    /**
     * @MethodName replaceTestdata
     * @Param problemId
     * @Param file
     * @Description 替换测试数据
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
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

    /**
     * @MethodName prepareTestdataDownload
     * @Param problemId
     * @Param version
     * @Description 准备测试数据下载
     * @Return @return {@link TestdataDownloadDecision }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
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

    /**
     * @MethodName writeTestdataZip
     * @Param problemId
     * @Param outputStream
     * @Description 写入测试数据zip
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public void writeTestdataZip(Long problemId, OutputStream outputStream) {
        requireProblem(problemId);
        fileStorageService.writeTestdataZip(problemId, outputStream);
    }

    /**
     * @MethodName writeTestdataCaseZip
     * @Param problemId
     * @Param caseNo
     * @Param outputStream
     * @Description 写入测试数据案例zip
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public void writeTestdataCaseZip(Long problemId, Integer caseNo, OutputStream outputStream) {
        requireProblem(problemId);
        fileStorageService.writeTestdataCaseZip(problemId, caseNo, outputStream);
    }

    /**
     * @MethodName requireProblem
     * @Param problemId
     * @Description 校验题目ID并获取题目对象
     * @Return @return {@link Problem }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
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
