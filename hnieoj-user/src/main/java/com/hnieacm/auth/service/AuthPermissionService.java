package com.hnieacm.auth.service;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/11
 * @Description: 权限服务接口
 */
public interface AuthPermissionService {

    /**
     * @MethodName getUserPermissions
     * @Param uid
     * @Description 获取用户权限列表
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    List<String> getUserPermissions(String uid);

    /**
     * @MethodName getUserRoles
     * @Param uid
     * @Description 获取用户角色列表
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    List<String> getUserRoles(String uid);
}
