package com.hnieacm.contest.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.contest.constant.ContestAuthConstant;
import com.hnieacm.contest.entity.Contest;
import com.hnieacm.contest.entity.ContestProblem;
import com.hnieacm.contest.entity.ContestRegister;
import com.hnieacm.contest.mapper.ContestMapper;
import com.hnieacm.contest.mapper.ContestProblemMapper;
import com.hnieacm.contest.mapper.ContestRegisterMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Active contest access must include the problem and honor private rosters. */
class ContestProblemAccessTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "contest-access-test");
        TableInfoHelper.initTableInfo(assistant, ContestProblem.class);
        TableInfoHelper.initTableInfo(assistant, ContestRegister.class);
    }

    @Test
    void publicContestAllowsRegisteredUserDuringContest() {
        Fixture fixture = fixture(ContestAuthConstant.PUBLIC);
        when(fixture.problemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(fixture.registerMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        fixture.service.checkProblemAccess(1L, 2L, "student");
    }

    @Test
    void privateContestRejectsUserWithoutApprovedRegistration() {
        Fixture fixture = fixture(ContestAuthConstant.PRIVATE);
        when(fixture.problemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(fixture.registerMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> fixture.service.checkProblemAccess(1L, 2L, "student"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("参赛资格");
        ArgumentCaptor<LambdaQueryWrapper<ContestRegister>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(fixture.registerMapper).selectCount(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("status");
        assertThat(query.getValue().getParamNameValuePairs()).containsValue(1);
    }

    @Test
    void rejectsProblemOutsideContest() {
        Fixture fixture = fixture(ContestAuthConstant.PUBLIC);
        when(fixture.problemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> fixture.service.checkProblemAccess(1L, 2L, "student"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不属于该比赛");
    }

    @Test
    void rejectsContestBeforeStart() {
        Fixture fixture = fixture(ContestAuthConstant.PUBLIC);
        Contest future = fixture.contest;
        future.setStartTime(LocalDateTime.now().plusHours(1));
        future.setEndTime(LocalDateTime.now().plusHours(2));

        assertThatThrownBy(() -> fixture.service.checkProblemAccess(1L, 2L, "student"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未开放");
        verifyNoInteractions(fixture.problemMapper);
    }

    private Fixture fixture(int auth) {
        ContestMapper contestMapper = mock(ContestMapper.class);
        ContestProblemMapper problemMapper = mock(ContestProblemMapper.class);
        ContestRegisterMapper registerMapper = mock(ContestRegisterMapper.class);
        Contest contest = new Contest();
        contest.setIsVisible(1);
        contest.setAuth(auth);
        contest.setStartTime(LocalDateTime.now().minusHours(1));
        contest.setEndTime(LocalDateTime.now().plusHours(1));
        when(contestMapper.selectById(1L)).thenReturn(contest);
        ContestQueryServiceImpl service = new ContestQueryServiceImpl(
                contestMapper, problemMapper, registerMapper, new ObjectMapper());
        return new Fixture(service, problemMapper, registerMapper, contest);
    }

    private record Fixture(ContestQueryServiceImpl service, ContestProblemMapper problemMapper,
                           ContestRegisterMapper registerMapper, Contest contest) {
    }
}
