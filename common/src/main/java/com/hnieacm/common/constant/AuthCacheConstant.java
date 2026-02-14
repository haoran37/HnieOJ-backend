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

    /**
     * 身份验证缓存数据类型: roles.
     * <p>
     * 用于日志记录和TTL路由，避免跨模块使用魔法字符串
     */
    public static final String ROLE_CACHE_TYPE = "roles";

    /**
     * 身份验证缓存数据类型: permissions.
     * <p>
     * 用于日志记录和TTL路由，避免跨模块使用魔法字符串
     */
    public static final String PERMISSION_CACHE_TYPE = "permissions";
}
