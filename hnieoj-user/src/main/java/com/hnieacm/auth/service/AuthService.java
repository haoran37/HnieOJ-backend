package com.hnieacm.auth.service;

import com.hnieacm.auth.dto.LoginRequest;
import com.hnieacm.auth.dto.LoginVo;
import com.hnieacm.auth.dto.RegisterRequest;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/12
 * @Description: 认证服务接口
 */
public interface AuthService {

    /**
     * @MethodName login
     * @Param request
     * @Description 用户登录
     * @Return @return {@link LoginVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    LoginVo login(LoginRequest request, String clientIp);

    /**
     * @MethodName register
     * @Param request
     * @Description 用户注册
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    void register(RegisterRequest request);
}
