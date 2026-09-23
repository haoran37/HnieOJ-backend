package com.hnieacm.contest.service;

import com.hnieacm.common.dto.ScoreSubmissionVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.contest.entity.Contest;
import com.hnieacm.contest.feign.ScoreSubmissionFeignClient;
import com.hnieacm.contest.mapper.ContestMapper;
import com.hnieacm.contest.mapper.ContestProblemMapper;
import com.hnieacm.contest.mapper.ContestRegisterMapper;
import com.hnieacm.contest.vo.ContestRankVo;
import com.hnieacm.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestRankServiceTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 23, 10, 0);

    @Test
    void privateScoreboardRejectsUnregisteredViewer() {
        ContestMapper contests = mock(ContestMapper.class);
        ContestRegisterMapper registrations = mock(ContestRegisterMapper.class);
        Contest privateContest = new Contest();
        privateContest.setIsVisible(1);
        privateContest.setOpenRank(1);
        privateContest.setAuth(1);
        privateContest.setStartTime(LocalDateTime.now().minusHours(1));
        privateContest.setEndTime(LocalDateTime.now().plusHours(1));
        when(contests.selectById(3L)).thenReturn(privateContest);
        when(registrations.selectCount(any())).thenReturn(0L);
        ContestRankService service = new ContestRankService(contests, mock(ContestProblemMapper.class),
                mock(ScoreSubmissionFeignClient.class), registrations);

        assertThrows(BizException.class, () -> service.standings(3L, "outsider", false));
    }

    @Test
    void repeatedRatingRequestsReuseRecentCalculation() {
        ContestMapper contests = mock(ContestMapper.class);
        ContestProblemMapper problems = mock(ContestProblemMapper.class);
        ScoreSubmissionFeignClient submissions = mock(ScoreSubmissionFeignClient.class);
        Contest finished = contest(0);
        finished.setId(1L);
        finished.setIsVisible(1);
        finished.setOpenRank(1);
        when(contests.selectList(any())).thenReturn(List.of(finished));
        when(problems.selectList(any())).thenReturn(List.of());
        when(submissions.listScores("contest", 1L)).thenReturn(Result.success(List.of()));
        ContestRankService service = new ContestRankService(contests, problems, submissions, mock(ContestRegisterMapper.class));

        service.ratings();
        service.ratings();

        verify(submissions, times(1)).listScores("contest", 1L);
    }

    @Test
    void acmRanksByAcceptedCountThenFirstAcceptedPenalty() {
        Contest contest = contest(0);
        List<ContestRankVo> rows = ContestRankService.calculate(contest, Set.of(1L), List.of(
                score("alice", 1L, 3, 0, 5),
                score("alice", 1L, 0, 100, 15),
                score("alice", 1L, 3, 0, 25),
                score("bob", 1L, 0, 100, 30)), START.plusHours(2));
        assertEquals("bob", rows.get(0).getUid());
        assertEquals(30, rows.get(0).getPenaltyMinutes());
        assertEquals("alice", rows.get(1).getUid());
        assertEquals(35, rows.get(1).getPenaltyMinutes());
    }

    @Test
    void oiUsesHighestScorePerProblemAndRespectsSealCutoff() {
        Contest contest = contest(1);
        List<ContestRankVo> rows = ContestRankService.calculate(contest, Set.of(1L), List.of(
                score("alice", 1L, 3, 30, 5),
                score("alice", 1L, 3, 75, 15),
                score("alice", 1L, 0, 100, 50),
                score("bob", 1L, 3, 60, 20)), START.plusMinutes(30));
        assertEquals("alice", rows.get(0).getUid());
        assertEquals(75, rows.get(0).getTotalScore());
        assertEquals(0, rows.get(0).getSolved());
    }

    private static Contest contest(int type) {
        Contest contest = new Contest();
        contest.setType(type);
        contest.setStartTime(START);
        contest.setEndTime(START.plusHours(2));
        return contest;
    }

    private static ScoreSubmissionVo score(String uid, long problemId, int status, int value, int minutes) {
        ScoreSubmissionVo row = new ScoreSubmissionVo();
        row.setUid(uid);
        row.setUsername(uid);
        row.setProblemId(problemId);
        row.setStatus(status);
        row.setScore(value);
        row.setGmtCreate(START.plusMinutes(minutes));
        return row;
    }
}
