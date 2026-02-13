package com.hnieacm.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.auth.entity.Permission;
import com.hnieacm.auth.entity.Role;
import com.hnieacm.auth.entity.RolePermission;
import com.hnieacm.auth.entity.UserRole;
import com.hnieacm.auth.mapper.PermissionMapper;
import com.hnieacm.auth.mapper.RoleMapper;
import com.hnieacm.auth.mapper.RolePermissionMapper;
import com.hnieacm.auth.mapper.UserRoleMapper;
import com.hnieacm.auth.service.AuthPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: 权限服务实现。
 * <p>
 * 说明：当前权限与角色映射基于 roleId 的简单规则；后续可替换为 DB RBAC 模型（role/permission 表）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthPermissionServiceImpl implements AuthPermissionService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;

    /**
     * @MethodName getUserPermissions
     * @Param uid
     * @Description 获取用户权限
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @Override
    public List<String> getUserPermissions(String uid) {
        try {
            List<Long> roleIds = getRoleIds(uid);
            if (roleIds.isEmpty()) {
                return Collections.emptyList();
            }

            // role_permission -> permission_id
            List<RolePermission> rolePermissions = rolePermissionMapper.selectList(
                    new LambdaQueryWrapper<RolePermission>().in(RolePermission::getRoleId, roleIds)
            );
            if (rolePermissions == null || rolePermissions.isEmpty()) {
                return Collections.emptyList();
            }

            Set<Long> permissionIds = rolePermissions.stream()
                    .map(RolePermission::getPermissionId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (permissionIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<Permission> permissions = permissionMapper.selectBatchIds(permissionIds);
            if (permissions == null || permissions.isEmpty()) {
                return Collections.emptyList();
            }

            return permissions.stream()
                    .map(Permission::getCode)
                    .filter(StrUtil::isNotBlank)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.error("Query user permissions failed, uid: {}", uid, e);
            return Collections.emptyList();
        }
    }

    /**
     * @MethodName getUserRoles
     * @Param uid
     * @Description 获取用户角色
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @Override
    public List<String> getUserRoles(String uid) {
        try {
            List<Long> roleIds = getRoleIds(uid);
            if (roleIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<Role> roles = roleMapper.selectBatchIds(roleIds);
            if (roles == null || roles.isEmpty()) {
                return Collections.emptyList();
            }

            // 按 roleId 升序优先（root/admin/teacher/...），保持稳定输出
            return roles.stream()
                    .sorted(Comparator.comparing(Role::getId))
                    .map(Role::getRole)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.error("Query user roles failed, uid: {}", uid, e);
            return Collections.emptyList();
        }
    }

    /**
     * @MethodName getRoleIds
     * @Param uid
     * @Description 获取角色ID
     * @Return @return {@link List }<{@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    private List<Long> getRoleIds(String uid) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserUid, uid)
        );
        if (userRoles == null || userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        return userRoles.stream()
                .map(UserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
