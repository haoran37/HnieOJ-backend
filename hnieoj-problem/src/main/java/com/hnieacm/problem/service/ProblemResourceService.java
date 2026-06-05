package com.hnieacm.problem.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 题目资源业务服务
 */
public interface ProblemResourceService {

    String getStatement(Long problemId);

    void updateStatement(Long problemId, String markdown);

    String uploadImage(Long problemId, MultipartFile file);

    void deleteImage(Long problemId, String filename);

    void replaceTestdata(Long problemId, MultipartFile file);

    TestdataDownloadDecision prepareTestdataDownload(Long problemId, Integer version);

    void writeTestdataZip(Long problemId, OutputStream outputStream);

    record TestdataDownloadDecision(boolean notModified, Integer dataVersion) {
    }
}
