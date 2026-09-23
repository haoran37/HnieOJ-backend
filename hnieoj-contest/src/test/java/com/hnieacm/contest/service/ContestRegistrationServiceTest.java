package com.hnieacm.contest.service;

import com.hnieacm.contest.entity.Contest;
import com.hnieacm.contest.entity.ContestRegister;
import com.hnieacm.contest.mapper.ContestMapper;
import com.hnieacm.contest.mapper.ContestRegisterMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestRegistrationServiceTest {
    @Test
    void concurrentDuplicateRegistrationSucceeds() {
        ContestMapper contests = mock(ContestMapper.class);
        ContestRegisterMapper registrations = mock(ContestRegisterMapper.class);
        Contest contest = new Contest();
        contest.setIsVisible(1);
        contest.setEndTime(LocalDateTime.now().plusDays(1));
        when(contests.selectById(1L)).thenReturn(contest);
        when(registrations.selectCount(any())).thenReturn(0L);
        when(registrations.insert(any(ContestRegister.class)))
                .thenThrow(new DuplicateKeyException("concurrent registration"));

        ContestRegistrationService service = new ContestRegistrationService(contests, registrations);
        assertDoesNotThrow(() -> service.register(1L, "student"));
        verify(registrations).insert(any(ContestRegister.class));
    }
}
