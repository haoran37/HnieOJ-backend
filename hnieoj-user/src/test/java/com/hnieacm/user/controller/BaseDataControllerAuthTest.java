package com.hnieacm.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: HaoRan_Lyu
 * @Description: 注册基础数据鉴权范围回归：三个注册选项查询对游客开放，教师/助教查询仍需登录。
 */
class BaseDataControllerAuthTest {

    @Test
    void classLevelLoginCheckIsRemoved() {
        assertThat(BaseDataController.class.isAnnotationPresent(SaCheckLogin.class)).isFalse();
    }

    @Test
    void registrationOptionQueriesArePublic() {
        assertThat(loginCheckOn("listColleges")).isFalse();
        assertThat(loginCheckOn("listGrades")).isFalse();
        assertThat(loginCheckOn("listClasses")).isFalse();
    }

    @Test
    void teacherAndTaQueriesKeepLoginCheck() {
        assertThat(loginCheckOn("listTeachers")).isTrue();
        assertThat(loginCheckOn("listTas")).isTrue();
    }

    private static boolean loginCheckOn(String methodName) {
        Method method = Arrays.stream(BaseDataController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName) && candidate.getParameterCount() >= 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing controller method: " + methodName));
        return method.isAnnotationPresent(SaCheckLogin.class);
    }
}
