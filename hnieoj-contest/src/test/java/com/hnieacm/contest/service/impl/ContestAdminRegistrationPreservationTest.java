package com.hnieacm.contest.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.contest.dto.AdminContestSaveRequest;
import com.hnieacm.contest.entity.Contest;
import com.hnieacm.contest.mapper.ContestMapper;
import com.hnieacm.contest.mapper.ContestProblemMapper;
import com.hnieacm.contest.mapper.ContestRegisterMapper;
import com.hnieacm.contest.mapper.ContestTeamMapper;
import com.hnieacm.contest.mapper.ContestTeamMemberMapper;
import com.hnieacm.contest.service.manager.ContestProblemManager;
import com.hnieacm.contest.service.manager.ContestUserManager;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestAdminRegistrationPreservationTest {
    @Test
    void savingPublicContestDoesNotDeleteSelfRegistrations() {
        ContestMapper contests = mock(ContestMapper.class);
        ContestRegisterMapper registrations = mock(ContestRegisterMapper.class);
        Contest existing = new Contest();
        existing.setId(1L);
        existing.setAuth(0);
        when(contests.selectById(1L)).thenReturn(existing);
        ContestAdminServiceImpl service = new ContestAdminServiceImpl(contests,
                mock(ContestProblemMapper.class), registrations, mock(ContestTeamMapper.class),
                mock(ContestTeamMemberMapper.class), mock(ContestProblemManager.class),
                mock(ContestUserManager.class), new ObjectMapper());
        AdminContestSaveRequest request = new AdminContestSaveRequest();
        request.setTitle("Updated title");
        request.setType("ACM");
        request.setAuth("Public");
        request.setStatus(true);
        request.setStartTime(1_800_000_000_000L);
        request.setEndTime(1_800_003_600_000L);

        service.updateContest(1L, request);

        verify(registrations, never()).delete(any());
    }
}
