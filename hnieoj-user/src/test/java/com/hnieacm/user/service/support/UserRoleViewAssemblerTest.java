package com.hnieacm.user.service.support;

import com.hnieacm.user.entity.Role;
import com.hnieacm.user.entity.UserRole;
import com.hnieacm.user.mapper.RoleMapper;
import com.hnieacm.user.mapper.UserRoleMapper;
import com.hnieacm.user.vo.UserListVo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: User role view assembler tests.
 */
class UserRoleViewAssemblerTest {

    @Test
    void shouldFillRolesWithStableOrderAndDistinctCodes() {
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        UserRoleViewAssembler assembler = new UserRoleViewAssembler(userRoleMapper, roleMapper);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(
                userRole("u1", 1004L),
                userRole("u1", 1001L),
                userRole("u1", 1001L)
        ));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(
                role(1001L, "admin"),
                role(1004L, "student")
        ));
        UserListVo vo = new UserListVo();
        vo.setUid("u1");

        assembler.fillRolesForUserList(List.of(vo));

        assertThat(vo.getRoles()).containsExactly("admin", "student");
    }

    private UserRole userRole(String uid, Long roleId) {
        UserRole userRole = new UserRole();
        userRole.setUserUid(uid);
        userRole.setRoleId(roleId);
        return userRole;
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setRole(code);
        return role;
    }
}
