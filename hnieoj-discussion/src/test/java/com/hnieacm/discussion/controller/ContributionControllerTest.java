package com.hnieacm.discussion.controller;

import com.hnieacm.common.result.Result;
import com.hnieacm.discussion.feign.ContributionUserClient;
import com.hnieacm.discussion.feign.dto.UserBasicDto;
import com.hnieacm.discussion.mapper.DiscussionMapper;
import com.hnieacm.discussion.vo.ContributionRankVo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContributionControllerTest {
    @Test
    void resolvesDisplayNameFromUserServiceInsteadOfAuthorUid() {
        DiscussionMapper discussions = mock(DiscussionMapper.class);
        ContributionUserClient users = mock(ContributionUserClient.class);
        ContributionRankVo row = new ContributionRankVo();
        row.setUid("20260001");
        when(discussions.listContributions()).thenReturn(List.of(row));
        UserBasicDto profile = new UserBasicDto();
        profile.setUid("20260001");
        profile.setUsername("Alice");
        when(users.query(any())).thenReturn(Result.success(List.of(profile)));

        var result = new ContributionController(discussions, users).list().getData();

        assertEquals("Alice", result.get(0).getUsername());
        assertEquals(1, result.get(0).getRank());
    }
}
