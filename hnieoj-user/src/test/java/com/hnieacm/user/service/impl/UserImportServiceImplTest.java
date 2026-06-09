package com.hnieacm.user.service.impl;

import com.hnieacm.user.dto.CreateUserRequest;
import com.hnieacm.user.service.UserManageService;
import com.hnieacm.user.vo.CreateUserVo;
import com.hnieacm.user.vo.UserImportResultVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户导入服务测试
 */
@ExtendWith(MockitoExtension.class)
class UserImportServiceImplTest {

    @Mock
    private UserManageService userManageService;

    @Test
    void shouldImportUsersFromExcel() {
        UserImportServiceImpl service = new UserImportServiceImpl(userManageService);
        when(userManageService.createUser(any())).thenReturn(new CreateUserVo("20220001", "HnieOJ@123456"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                service.buildTemplate()
        );

        UserImportResultVo result = service.importUsers(file);

        ArgumentCaptor<CreateUserRequest> requestCaptor = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(userManageService).createUser(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getUid()).isEqualTo("20220001");
        assertThat(requestCaptor.getValue().getUsername()).isEqualTo("张三");
        assertThat(requestCaptor.getValue().getCollegeId()).isEqualTo(1L);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getCreatedUsers()).singleElement()
                .satisfies(item -> assertThat(item.getInitialPassword()).isEqualTo("HnieOJ@123456"));
    }
}
