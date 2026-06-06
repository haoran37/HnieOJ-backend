package com.hnieacm.problem.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 题目本地文件存储服务
 */
public interface ProblemFileStorageService {

    String saveImage(Long problemId, MultipartFile file);

    void deleteImage(Long problemId, String filename);

    void validateTestdata(MultipartFile file);

    void replaceTestdata(Long problemId, MultipartFile file);

    void writeTestdataZip(Long problemId, OutputStream outputStream);

    boolean hasAvailableTestdata(Long problemId);
}
