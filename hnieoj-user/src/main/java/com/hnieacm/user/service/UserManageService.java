package com.hnieacm.user.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.user.dto.BatchUidsRequest;
import com.hnieacm.user.dto.CreateUserRequest;
import com.hnieacm.user.dto.GrantPermissionRequest;
import com.hnieacm.user.dto.UpdateUserPasswordRequest;
import com.hnieacm.user.dto.UpdateUserPermissionRequest;
import com.hnieacm.user.dto.UpdateUserRequest;
import com.hnieacm.user.vo.CreateUserVo;
import com.hnieacm.user.vo.PermissionUserVo;
import com.hnieacm.user.vo.UserSearchVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 用户管理服务。
 */
public interface UserManageService {

    /**
     * 创建用户
     */
    CreateUserVo createUser(CreateUserRequest request);

    /**
     * 更新用户信息
     */
    void updateUser(String uid, UpdateUserRequest request);

    /**
     * 更新用户密码
     */
    void updateUserPassword(String uid, UpdateUserPasswordRequest request);

    /**
     * 删除用户
     */
    void deleteUser(String uid);

    /**
     * 批量禁用用户
     */
    void batchDisableUsers(BatchUidsRequest request);

    /**
     * 批量启用用户
     */
    void batchEnableUsers(BatchUidsRequest request);

    /**
     * 批量删除用户
     */
    void batchDeleteUsers(BatchUidsRequest request);

    /**
     * 获取权限用户列表
     */
    PageVo<PermissionUserVo> getPermissionUsers(int page, int pageSize);

    /**
     * 搜索用户
     */
    PageVo<UserSearchVo> searchUsers(String query, int page, int pageSize);

    /**
     * 授予用户权限
     */
    void grantPermissions(GrantPermissionRequest request);

    /**
     * 更新用户权限
     */
    void updateUserPermission(UpdateUserPermissionRequest request);

    /**
     * 撤销用户权限
     */
    void revokePermission(String uid);

    /**
     * 批量撤销用户权限
     */
    void batchRevokePermissions(BatchUidsRequest request);
}

