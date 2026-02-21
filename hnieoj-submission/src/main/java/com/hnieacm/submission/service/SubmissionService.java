package com.hnieacm.submission.service;

import com.hnieacm.submission.dto.SubmitCodeRequest;
import com.hnieacm.submission.vo.SubmitCodeVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 提交服务
 */
public interface SubmissionService {

    SubmitCodeVo submit(SubmitCodeRequest request, MultipartFile file);
}

