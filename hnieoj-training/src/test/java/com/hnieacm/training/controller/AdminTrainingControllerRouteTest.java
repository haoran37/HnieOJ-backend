package com.hnieacm.training.controller;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.training.service.TrainingAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 管理端题单路由回归：/list 是字面量路由，不能被新增的 GET /{id} 遮蔽
 */
class AdminTrainingControllerRouteTest {

    @Test
    void listRouteIsNotShadowedByPathVariableRoute() throws Exception {
        TrainingAdminService service = mock(TrainingAdminService.class);
        when(service.listTrainings(1, 20, null, null, null, null))
                .thenReturn(new PageVo<>(List.of(), 0));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminTrainingController(service)).build();

        mockMvc.perform(get("/api/admin/training/list").param("page", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());

        verify(service).listTrainings(1, 20, null, null, null, null);
    }
}
