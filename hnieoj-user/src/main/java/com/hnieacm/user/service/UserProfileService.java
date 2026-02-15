package com.hnieacm.user.service;

import com.hnieacm.user.vo.UserProfileVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 用户信息服务
 */
public interface UserProfileService {

    /**
     * 获取当前登录用户信息
     */
    UserProfileVo getCurrentUserProfile();
}

