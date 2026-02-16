package com.hnieacm.auth.service;

import com.hnieacm.auth.vo.RegistrationApplyVo;
import com.hnieacm.common.dto.PageVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 注册申请查询服务
 */
public interface RegistrationApplyQueryService {

    /**
     * 分页查询注册申请列表
     */
    PageVo<RegistrationApplyVo> list(int page, int pageSize, Integer status, String keyword);
}

