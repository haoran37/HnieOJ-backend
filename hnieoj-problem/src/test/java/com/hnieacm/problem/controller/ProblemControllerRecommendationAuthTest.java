package com.hnieacm.problem.controller;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.result.Result;
import com.hnieacm.problem.service.ProblemQueryService;
import com.hnieacm.problem.service.ProblemResourceService;
import com.hnieacm.problem.vo.ProblemListVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 推荐接口登录态回归：hnieoj-problem 未启用 SaInterceptor，
 * 控制器须显式拒绝匿名直连，同时放行已登录用户。
 */
class ProblemControllerRecommendationAuthTest {

    private ProblemQueryService problemQueryService;
    private ProblemController controller;

    @BeforeEach
    void setUp() {
        SaTokenContextMockUtil.clearContext();
        problemQueryService = mock(ProblemQueryService.class);
        controller = new ProblemController(problemQueryService, mock(ProblemResourceService.class));
    }

    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
    }

    @Test
    void anonymousDirectCallIsRejectedBeforeService() {
        // 初始化 SaToken 上下文但不登录：显式 checkLogin 必须抛 NotLoginException
        SaTokenContextMockUtil.setMockContext(() ->
                assertThatThrownBy(() -> controller.recommendations("P1", 5))
                        .isInstanceOf(NotLoginException.class));
        verifyNoInteractions(problemQueryService);
    }

    @Test
    void loggedInUserCanReadRecommendations() {
        when(problemQueryService.getRecommendations("P1", 5)).thenReturn(List.of());

        SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login("alice");
            Result<List<ProblemListVo>> result = controller.recommendations("P1", 5);
            assertThat(result.getData()).isEmpty();
        });

        verify(problemQueryService).getRecommendations("P1", 5);
    }
}
