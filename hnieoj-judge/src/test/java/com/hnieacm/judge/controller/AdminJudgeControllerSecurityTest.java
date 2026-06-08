package com.hnieacm.judge.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.common.constant.RoleConstant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: Admin judge controller security tests.
 */
class AdminJudgeControllerSecurityTest {

    @ParameterizedTest
    @ValueSource(classes = {
            AdminJudgeController.class,
            AdminJudgeNodeController.class,
            AdminJudgeServerController.class,
            AdminSystemConfigController.class
    })
    void shouldRequireAdminOrRootRole(Class<?> controllerClass) {
        SaCheckRole annotation = controllerClass.getAnnotation(SaCheckRole.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.mode()).isEqualTo(SaMode.OR);
        assertThat(annotation.value()).containsExactlyInAnyOrder(RoleConstant.ADMIN, RoleConstant.ROOT);
    }
}
