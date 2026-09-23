package com.hnieacm.training.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.training.service.HomeworkClassAccessService;
import com.hnieacm.training.service.HomeworkQueryService;
import com.hnieacm.training.service.HomeworkRankService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeworkControllerClassAccessTest {
    @Test
    void studentCannotOverrideClassFilterFromRequest() {
        HomeworkQueryService queries = mock(HomeworkQueryService.class);
        HomeworkClassAccessService access = mock(HomeworkClassAccessService.class);
        when(access.currentClassId("student", "Bearer token")).thenReturn(7L);
        when(queries.listHomeworks(1, 10, null, 7L, null)).thenReturn(new PageVo<>(List.of(), 0L));
        HomeworkController controller = new HomeworkController(queries, mock(HomeworkRankService.class), access);

        try (MockedStatic<StpUtil> login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsString).thenReturn("student");
            controller.list(1, 10, null, 99L, List.of(99L), "Bearer token");
        }

        verify(queries).listHomeworks(1, 10, null, 7L, null);
    }
}
