package com.hnieacm.training.controller;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.training.constant.HomeworkStatusConstant;
import com.hnieacm.training.entity.Homework;
import com.hnieacm.training.feign.HomeworkUserFeignClient;
import com.hnieacm.training.mapper.HomeworkClassMapper;
import com.hnieacm.training.mapper.HomeworkMapper;
import com.hnieacm.training.mapper.HomeworkProblemMapper;
import com.hnieacm.training.service.HomeworkClassAccessService;
import com.hnieacm.training.vo.HomeworkUserVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalHomeworkAccessControllerTest {
    private final HomeworkMapper homeworks = mock(HomeworkMapper.class);
    private final HomeworkProblemMapper problems = mock(HomeworkProblemMapper.class);
    private final HomeworkClassMapper classes = mock(HomeworkClassMapper.class);
    private final HomeworkUserFeignClient users = mock(HomeworkUserFeignClient.class);
    private final HomeworkClassAccessService classAccess = new HomeworkClassAccessService(classes, users);
    private final InternalHomeworkAccessController controller =
            new InternalHomeworkAccessController(homeworks, problems, classAccess);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "internalToken", "internal-test-token");
        Homework homework = new Homework();
        homework.setStatus(HomeworkStatusConstant.ENABLED);
        homework.setStartTime(LocalDateTime.now().minusDays(1));
        homework.setEndTime(LocalDateTime.now().plusDays(1));
        when(homeworks.selectById(1L)).thenReturn(homework);
        when(problems.selectCount(any())).thenReturn(1L);
        HomeworkUserVo user = new HomeworkUserVo();
        user.setUid("student");
        user.setClassId(7L);
        when(users.getUserDetail("student", "Bearer student-token")).thenReturn(Result.success(user));
    }

    @Test
    void assignedClassCanSubmit() {
        when(classes.selectCount(any())).thenReturn(1L);
        assertEquals(Boolean.TRUE,
                controller.check(1L, 2L, "student", "Bearer student-token", "internal-test-token").getData());
    }

    @Test
    void userOutsideAssignedClassesCannotSubmit() {
        when(classes.selectCount(any())).thenReturn(0L);
        assertThrows(BizException.class,
                () -> controller.check(1L, 2L, "student", "Bearer student-token", "internal-test-token"));
    }

    @Test
    void missingUserAuthorizationCannotSubmit() {
        assertThrows(BizException.class,
                () -> controller.check(1L, 2L, "student", null, "internal-test-token"));
    }
}
