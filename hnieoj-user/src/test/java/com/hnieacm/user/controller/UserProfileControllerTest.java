package com.hnieacm.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.user.dto.ChangeCurrentPasswordRequest;
import com.hnieacm.user.service.UserManageService;
import com.hnieacm.user.service.UserProfileChangeService;
import com.hnieacm.user.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: User profile controller tests.
 */
class UserProfileControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBindOldPasswordWhenChangingCurrentPassword() throws Exception {
        UserProfileService userProfileService = mock(UserProfileService.class);
        UserManageService userManageService = mock(UserManageService.class);
        UserProfileChangeService userProfileChangeService = mock(UserProfileChangeService.class);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new UserProfileController(userProfileService, userManageService, userProfileChangeService))
                .build();

        mockMvc.perform(put("/api/user/profile/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newPasswordRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ChangeCurrentPasswordRequest> requestCaptor =
                ArgumentCaptor.forClass(ChangeCurrentPasswordRequest.class);
        verify(userProfileService).changeCurrentPassword(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getOldPassword()).isEqualTo("Old@123456");
        assertThat(requestCaptor.getValue().getPassword()).isEqualTo("New@123456");
    }

    private ChangeCurrentPasswordRequest newPasswordRequest() {
        ChangeCurrentPasswordRequest request = new ChangeCurrentPasswordRequest();
        request.setOldPassword("Old@123456");
        request.setPassword("New@123456");
        return request;
    }
}
