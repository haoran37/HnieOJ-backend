package com.hnieacm.auth.service;

import com.hnieacm.auth.vo.RegistrationImportResultVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 注册信息导入服务
 */
public interface RegistrationImportService {

    /**
     * 导入注册信息并自动审核通过
     */
    RegistrationImportResultVo importAndApprove(MultipartFile file);
}
