package com.hnieacm.user.service;

import com.hnieacm.user.vo.UserProfileVo;
import com.hnieacm.user.dto.ChangeCurrentPasswordRequest;

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

    /**
     * 修改当前登录用户密码
     */
    void changeCurrentPassword(ChangeCurrentPasswordRequest request);
}
