package com.hnieacm.auth.service.impl;

import com.hnieacm.auth.dto.RegisterRequest;
import com.hnieacm.auth.service.AuthService;
import com.hnieacm.auth.service.RegistrationReviewService;
import com.hnieacm.auth.vo.RegistrationImportResultVo;
import com.hnieacm.common.util.SimpleExcelUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 注册信息导入服务测试
 */
@ExtendWith(MockitoExtension.class)
class RegistrationImportServiceImplTest {

    @Mock
    private AuthService authService;

    @Mock
    private RegistrationReviewService registrationReviewService;

    @Test
    void shouldRegisterAndApproveImportedRows() {
        RegistrationImportServiceImpl service = new RegistrationImportServiceImpl(authService, registrationReviewService);
        byte[] content = SimpleExcelUtils.buildTemplate(
                "注册导入",
                List.of("uid", "username", "password", "email", "collegeId", "classId", "grade", "qq"),
                List.of(List.of("20220001", "张三", "HnieOJ@123456", "zhangsan@example.com", "1", "1", "2022", "123456"))
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "registrations.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );

        RegistrationImportResultVo result = service.importAndApprove(file);

        ArgumentCaptor<RegisterRequest> requestCaptor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(authService).register(requestCaptor.capture());
        verify(registrationReviewService).approve("20220001");
        assertThat(requestCaptor.getValue().getUid()).isEqualTo("20220001");
        assertThat(requestCaptor.getValue().getCollegeId()).isEqualTo(1L);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getSuccessUids()).containsExactly("20220001");
    }
}
