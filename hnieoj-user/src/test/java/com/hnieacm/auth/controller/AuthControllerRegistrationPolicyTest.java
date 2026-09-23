package com.hnieacm.auth.controller;

import com.hnieacm.auth.dto.RegisterRequest;
import com.hnieacm.auth.service.AuthService;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.user.feign.SubmissionInternalFeignClient;
import com.hnieacm.user.vo.RegisterEmailCheckVo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Registration must enforce the saved system policy before writing an application. */
class AuthControllerRegistrationPolicyTest {

    @Test
    void rejectsDisabledRegistration() {
        AuthService authService = mock(AuthService.class);
        SubmissionInternalFeignClient client = mock(SubmissionInternalFeignClient.class);
        RegisterRequest request = request();
        RegisterEmailCheckVo check = new RegisterEmailCheckVo();
        check.setMatched(false);
        check.setReason("系统暂未开放注册");
        when(client.checkRegisterEmail(request.getEmail())).thenReturn(Result.success(check));

        assertThatThrownBy(() -> new AuthController(authService, client).register(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("系统暂未开放注册");
        verifyNoInteractions(authService);
    }

    @Test
    void permitsAllowedEmail() {
        AuthService authService = mock(AuthService.class);
        SubmissionInternalFeignClient client = mock(SubmissionInternalFeignClient.class);
        RegisterRequest request = request();
        RegisterEmailCheckVo check = new RegisterEmailCheckVo();
        check.setAllowRegister(true);
        check.setMatched(true);
        when(client.checkRegisterEmail(request.getEmail())).thenReturn(Result.success(check));

        new AuthController(authService, client).register(request);

        verify(authService).register(request);
    }

    @Test
    void rejectsMatchedEmailWhenRegistrationIsDisabled() {
        AuthService authService = mock(AuthService.class);
        SubmissionInternalFeignClient client = mock(SubmissionInternalFeignClient.class);
        RegisterRequest request = request();
        RegisterEmailCheckVo check = new RegisterEmailCheckVo();
        check.setAllowRegister(false);
        check.setMatched(true);
        check.setReason("系统暂未开放注册");
        when(client.checkRegisterEmail(request.getEmail())).thenReturn(Result.success(check));

        assertThatThrownBy(() -> new AuthController(authService, client).register(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("系统暂未开放注册");
        verifyNoInteractions(authService);
    }

    @Test
    void usesInvitationRegistrationInInviteMode() {
        AuthService authService = mock(AuthService.class);
        SubmissionInternalFeignClient client = mock(SubmissionInternalFeignClient.class);
        RegisterRequest request = request();
        request.setInviteCode("one-time-code");
        RegisterEmailCheckVo check = new RegisterEmailCheckVo();
        check.setAllowRegister(true);
        check.setRegisterMode("INVITE_CODE");
        check.setMatched(false);
        when(client.checkRegisterEmail(request.getEmail())).thenReturn(Result.success(check));

        new AuthController(authService, client).register(request);

        verify(authService).registerWithInvite(request);
    }

    @Test
    void failsClosedWhenPolicyIsUnavailable() {
        AuthService authService = mock(AuthService.class);
        SubmissionInternalFeignClient client = mock(SubmissionInternalFeignClient.class);
        RegisterRequest request = request();

        assertThatThrownBy(() -> new AuthController(authService, client).register(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("注册策略暂不可用");
        verifyNoInteractions(authService);
    }

    private RegisterRequest request() {
        RegisterRequest request = new RegisterRequest();
        request.setUid("20260001");
        request.setEmail("student@hnie.edu.cn");
        return request;
    }
}
