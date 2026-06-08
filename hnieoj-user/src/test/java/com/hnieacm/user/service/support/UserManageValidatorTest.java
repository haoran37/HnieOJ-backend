package com.hnieacm.user.service.support;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.constant.UserStatusConstant;
import com.hnieacm.user.dto.BatchUidsRequest;
import com.hnieacm.user.properties.UserManageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: User management validator tests.
 */
class UserManageValidatorTest {

    private UserManageValidator validator;

    @BeforeEach
    void setUp() {
        UserManageProperties properties = new UserManageProperties();
        properties.setUsernameMinLength(2);
        properties.setUsernameMaxLength(20);
        properties.setPasswordMinLength(6);
        properties.setPasswordMaxLength(32);
        validator = new UserManageValidator(properties);
    }

    @Test
    void shouldKeepDefaultCreateUserPassword() {
        assertThat(validator.generateDefaultPassword()).isEqualTo("HnieOJ@123456");
    }

    @Test
    void shouldNormalizeUidsByTrimmingAndDeduplicating() {
        BatchUidsRequest request = new BatchUidsRequest();
        request.setUids(List.of(" u1 ", "", "u2", "u1", "   "));

        assertThat(validator.normalizeUids(request)).containsExactly("u1", "u2");
    }

    @Test
    void shouldRejectIllegalStatus() {
        assertThat(validator.resolveStatus(UserStatusConstant.NORMAL)).isEqualTo(UserStatusConstant.NORMAL);
        assertThatThrownBy(() -> validator.resolveStatus(99))
                .isInstanceOf(BizException.class);
    }
}
