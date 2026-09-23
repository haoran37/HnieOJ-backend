package com.hnieacm.user.controller;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.user.mapper.UserFavoriteMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class UserFavoriteControllerTest {
    @Test
    void missingTypeReturnsValidationError() {
        UserFavoriteController.FavoriteRequest request = new UserFavoriteController.FavoriteRequest();
        request.setTargetId("P1000");
        UserFavoriteController controller = new UserFavoriteController(mock(UserFavoriteMapper.class));
        assertThrows(BizException.class, () -> controller.add(request));
    }
}
