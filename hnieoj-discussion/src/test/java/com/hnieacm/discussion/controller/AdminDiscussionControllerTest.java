package com.hnieacm.discussion.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.common.constant.RoleConstant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: Admin discussion controller security tests.
 */
class AdminDiscussionControllerTest {

    @Test
    void shouldRequireAdminOrRootRole() {
        SaCheckRole annotation = AdminDiscussionController.class.getAnnotation(SaCheckRole.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.mode()).isEqualTo(SaMode.OR);
        assertThat(annotation.value()).containsExactlyInAnyOrder(RoleConstant.ADMIN, RoleConstant.ROOT);
    }
}
