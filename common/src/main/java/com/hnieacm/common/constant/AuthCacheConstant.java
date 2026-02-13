package com.hnieacm.common.constant;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: 网关鉴权用的角色/权限缓存 Key 约定
 * <p>
 * 说明：缓存由认证服务写入，网关读取。
 */
public class AuthCacheConstant {

    private AuthCacheConstant() {
    }

    public static final String ROLE_CACHE_PREFIX = "auth:roles:";
    public static final String PERMISSION_CACHE_PREFIX = "auth:permissions:";
}
