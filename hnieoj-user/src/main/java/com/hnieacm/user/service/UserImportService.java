package com.hnieacm.user.service;

import com.hnieacm.user.vo.UserImportResultVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户导入服务
 */
public interface UserImportService {

    /**
     * 生成用户导入模板
     */
    byte[] buildTemplate();

    /**
     * 导入用户
     */
    UserImportResultVo importUsers(MultipartFile file);
}
