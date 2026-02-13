package com.hnieacm.auth.service;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: 用户鉴权信息缓存服务（供 Gateway 鉴权读取）
 */
public interface UserAuthCacheService {

    /**
     * @MethodName cacheUserAuth
     * @Param uid
     * @Description 缓存用户的角色与权限信息到 Redis
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    void cacheUserAuth(String uid);
}

