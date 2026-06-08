package com.hnieacm.user.service.support;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.user.entity.Role;
import com.hnieacm.user.entity.UserRole;
import com.hnieacm.user.mapper.RoleMapper;
import com.hnieacm.user.mapper.UserRoleMapper;
import com.hnieacm.user.vo.PermissionUserVo;
import com.hnieacm.user.vo.UserListVo;
import com.hnieacm.user.vo.UserSearchVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: User role view assembler.
 */
@Component
@RequiredArgsConstructor
public class UserRoleViewAssembler {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public void fillRolesForPermissionUsers(List<PermissionUserVo> records) {
        fillRolesForUsers(records, PermissionUserVo::getUid, PermissionUserVo::setRoles);
    }

    public void fillRolesForSearchedUsers(List<UserSearchVo> records) {
        fillRolesForUsers(records, UserSearchVo::getUid, UserSearchVo::setRoles);
    }

    public void fillRolesForUserList(List<UserListVo> records) {
        fillRolesForUsers(records, UserListVo::getUid, UserListVo::setRoles);
    }

    public List<String> getRoles(String uid) {
        if (StrUtil.isBlank(uid)) {
            return List.of();
        }
        return queryUserRolesMap(List.of(uid)).getOrDefault(uid, Collections.emptyList());
    }

    private <T> void fillRolesForUsers(List<T> records,
                                       Function<T, String> uidExtractor,
                                       BiConsumer<T, List<String>> roleSetter) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<String> uids = records.stream()
                .map(uidExtractor)
                .filter(StrUtil::isNotBlank)
                .toList();
        Map<String, List<String>> rolesMap = queryUserRolesMap(uids);
        for (T record : records) {
            String uid = uidExtractor.apply(record);
            roleSetter.accept(record, rolesMap.getOrDefault(uid, Collections.emptyList()));
        }
    }

    private Map<String, List<String>> queryUserRolesMap(List<String> uids) {
        if (uids == null || uids.isEmpty()) {
            return Map.of();
        }
        List<UserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().in(UserRole::getUserUid, uids));
        if (userRoles == null || userRoles.isEmpty()) {
            return Map.of();
        }
        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        if (roles == null || roles.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> roleIdToCode = roles.stream()
                .filter(r -> r.getId() != null && StrUtil.isNotBlank(r.getRole()))
                .collect(Collectors.toMap(Role::getId, Role::getRole, (a, b) -> a));

        return userRoles.stream()
                .filter(ur -> StrUtil.isNotBlank(ur.getUserUid()) && ur.getRoleId() != null)
                .sorted(Comparator.comparingLong(UserRole::getRoleId))
                .collect(Collectors.groupingBy(
                        UserRole::getUserUid,
                        Collectors.mapping(ur -> roleIdToCode.getOrDefault(ur.getRoleId(), ""), Collectors.toList())
                ))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().filter(StrUtil::isNotBlank).distinct().toList()
                ));
    }
}
