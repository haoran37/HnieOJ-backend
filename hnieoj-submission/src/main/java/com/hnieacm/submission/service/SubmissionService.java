package com.hnieacm.submission.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.submission.dto.SubmissionListQueryRequest;
import com.hnieacm.submission.dto.SubmitCodeRequest;
import com.hnieacm.submission.vo.SubmissionCaseVo;
import com.hnieacm.submission.vo.SubmissionDetailVo;
import com.hnieacm.submission.vo.SubmissionListItemVo;
import com.hnieacm.submission.vo.SubmitCodeVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 提交服务
 */
public interface SubmissionService {

    SubmitCodeVo submit(SubmitCodeRequest request, MultipartFile file);

    PageVo<SubmissionListItemVo> listSubmissions(SubmissionListQueryRequest request);

    SubmissionDetailVo getSubmissionDetail(String submissionId);

    List<SubmissionCaseVo> listSubmissionCases(String submissionId);
}
