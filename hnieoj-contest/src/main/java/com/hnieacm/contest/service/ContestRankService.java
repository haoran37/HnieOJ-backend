package com.hnieacm.contest.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.dto.ScoreSubmissionVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.contest.feign.ScoreSubmissionFeignClient;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.contest.constant.ContestTypeConstant;
import com.hnieacm.contest.constant.ContestAuthConstant;
import com.hnieacm.contest.entity.Contest;
import com.hnieacm.contest.entity.ContestRegister;
import com.hnieacm.contest.entity.ContestProblem;
import com.hnieacm.contest.mapper.ContestMapper;
import com.hnieacm.contest.mapper.ContestProblemMapper;
import com.hnieacm.contest.mapper.ContestRegisterMapper;
import com.hnieacm.contest.vo.ContestRankVo;
import com.hnieacm.contest.vo.ContestRatingVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author HnieOJ contributors
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContestRankService {
    private static final long RATINGS_CACHE_NANOS = Duration.ofSeconds(10).toNanos();
    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ScoreSubmissionFeignClient submissionClient;
    private final ContestRegisterMapper contestRegisterMapper;
    private volatile RatingSnapshot ratingSnapshot;

    /** Ratings start at 1200; each finished contest moves 25% toward rank percentile performance (800–2000). */
    public List<ContestRatingVo> ratings() {
        RatingSnapshot snapshot = ratingSnapshot;
        if (snapshot != null && System.nanoTime() - snapshot.createdAtNanos() < RATINGS_CACHE_NANOS) {
            return snapshot.rows();
        }
        synchronized (this) {
            snapshot = ratingSnapshot;
            if (snapshot != null && System.nanoTime() - snapshot.createdAtNanos() < RATINGS_CACHE_NANOS) {
                return snapshot.rows();
            }
            List<ContestRatingVo> rows = List.copyOf(calculateRatings());
            ratingSnapshot = new RatingSnapshot(rows, System.nanoTime());
            return rows;
        }
    }

    private List<ContestRatingVo> calculateRatings() {
        LocalDateTime now = LocalDateTime.now();
        List<Contest> finished = contestMapper.selectList(new LambdaQueryWrapper<Contest>()
                .eq(Contest::getIsVisible, 1).eq(Contest::getOpenRank, 1)
                .le(Contest::getEndTime, now).orderByAsc(Contest::getEndTime).orderByAsc(Contest::getId));
        Map<String, ContestRatingVo> ratings = new HashMap<>(16);
        for (Contest contest : finished) {
            Set<Long> problemIds = contestProblemMapper.selectList(new LambdaQueryWrapper<ContestProblem>()
                    .eq(ContestProblem::getCid, contest.getId())).stream()
                    .map(ContestProblem::getProblemId).collect(Collectors.toSet());
            Result<List<ScoreSubmissionVo>> response;
            try {
                response = submissionClient.listScores("contest", contest.getId());
            } catch (RuntimeException exception) {
                log.warn("Skipping unavailable contest scores for rating, contestId={}", contest.getId(), exception);
                continue;
            }
            if (response == null || response.getCode() != ResultCode.SUCCESS || response.getData() == null) {
                log.warn("Skipping unavailable contest scores for rating, contestId={}", contest.getId());
                continue;
            }
            List<ContestRankVo> standings = calculate(contest, problemIds, response.getData(), contest.getEndTime());
            int participants = standings.size();
            for (ContestRankVo standing : standings) {
                ContestRatingVo rating = ratings.computeIfAbsent(standing.getUid(), uid -> {
                    ContestRatingVo row = new ContestRatingVo();
                    row.setUid(uid);
                    row.setRating(1200);
                    return row;
                });
                rating.setUsername(standing.getUsername());
                int performance = participants == 1 ? 1200
                        : 800 + Math.round(1200f * (participants - standing.getRank()) / (participants - 1));
                rating.setRating(Math.round(0.75f * rating.getRating() + 0.25f * performance));
                rating.setContests(rating.getContests() + 1);
            }
        }
        List<ContestRatingVo> rows = new ArrayList<>(ratings.values());
        rows.sort(Comparator.comparingInt(ContestRatingVo::getRating).reversed()
                .thenComparing(Comparator.comparingInt(ContestRatingVo::getContests).reversed())
                .thenComparing(ContestRatingVo::getUid));
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setRank(i + 1);
        }
        return rows;
    }

    private record RatingSnapshot(List<ContestRatingVo> rows, long createdAtNanos) { }

    public List<ContestRankVo> standings(long contestId, String viewerUid, boolean manager) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null || !Integer.valueOf(1).equals(contest.getIsVisible())) {
            throw new BizException(ResultCode.NOT_FOUND, "比赛不存在或不可见");
        }
        if (!Integer.valueOf(1).equals(contest.getOpenRank())) {
            throw new BizException(ResultCode.FORBIDDEN, "比赛未开放榜单");
        }
        if (Integer.valueOf(ContestAuthConstant.PRIVATE).equals(contest.getAuth()) && !manager
                && contestRegisterMapper.selectCount(new LambdaQueryWrapper<ContestRegister>()
                        .eq(ContestRegister::getCid, contestId)
                        .eq(ContestRegister::getUid, viewerUid)
                        .eq(ContestRegister::getStatus, 1)) == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "未获得该比赛的参赛资格");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(contest.getStartTime())) {
            throw new BizException(ResultCode.FORBIDDEN, "比赛尚未开始");
        }
        List<ContestProblem> problems = contestProblemMapper.selectList(new LambdaQueryWrapper<ContestProblem>()
                .eq(ContestProblem::getCid, contestId));
        Set<Long> problemIds = problems.stream().map(ContestProblem::getProblemId).collect(Collectors.toSet());
        Result<List<ScoreSubmissionVo>> response = submissionClient.listScores("contest", contestId);
        if (response == null || response.getCode() != ResultCode.SUCCESS || response.getData() == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "比赛成绩暂不可用");
        }
        LocalDateTime cutoff = now.isBefore(contest.getEndTime()) && Integer.valueOf(1).equals(contest.getSealRank())
                && contest.getSealRankTime() != null && now.isAfter(contest.getSealRankTime())
                ? contest.getSealRankTime() : now;
        return calculate(contest, problemIds, response.getData(), cutoff);
    }

    static List<ContestRankVo> calculate(Contest contest, Set<Long> problemIds,
                                         List<ScoreSubmissionVo> submissions, LocalDateTime cutoff) {
        Map<String, Participant> participants = new HashMap<>(16);
        for (ScoreSubmissionVo submission : submissions) {
            LocalDateTime submittedAt = submission.getGmtCreate();
            if (submission.getUid() == null || submittedAt == null || submittedAt.isBefore(contest.getStartTime())
                    || submittedAt.isAfter(contest.getEndTime()) || submittedAt.isAfter(cutoff)
                    || !problemIds.contains(submission.getProblemId()) || submission.getStatus() == null
                    || submission.getStatus() < 0 || submission.getStatus() >= 6) {
                continue;
            }
            Participant participant = participants.computeIfAbsent(submission.getUid(),
                    uid -> new Participant(uid, submission.getUsername()));
            participant.username = submission.getUsername();
            ProblemResult result = participant.problems.computeIfAbsent(submission.getProblemId(),
                    ignored -> new ProblemResult());
            if (contest.getType() != null && contest.getType() == ContestTypeConstant.OI) {
                int score = submission.getStatus() == 0 ? 100 : Math.min(100, Math.max(0, submission.getScore() == null ? 0 : submission.getScore()));
                result.score = Math.max(result.score, score);
            } else if (!result.accepted) {
                if (submission.getStatus() == 0) {
                    result.accepted = true;
                    result.score = 100;
                    result.penaltyMinutes = Duration.between(contest.getStartTime(), submittedAt).toMinutes()
                            + 20L * result.wrongAttempts;
                } else {
                    result.wrongAttempts++;
                }
            }
        }
        boolean oi = contest.getType() != null && contest.getType() == ContestTypeConstant.OI;
        List<ContestRankVo> rows = new ArrayList<>();
        for (Participant participant : participants.values()) {
            ContestRankVo row = new ContestRankVo();
            row.setUid(participant.uid);
            row.setUsername(participant.username);
            Map<Long, Integer> scores = new LinkedHashMap<>();
            for (Map.Entry<Long, ProblemResult> entry : participant.problems.entrySet()) {
                ProblemResult result = entry.getValue();
                scores.put(entry.getKey(), result.score);
                if (result.score == 100) {
                    row.setSolved(row.getSolved() + 1);
                }
                row.setTotalScore(row.getTotalScore() + result.score);
                row.setPenaltyMinutes(row.getPenaltyMinutes() + result.penaltyMinutes);
            }
            row.setProblemScores(scores);
            rows.add(row);
        }
        rows.sort(oi
                ? Comparator.comparingInt(ContestRankVo::getTotalScore).reversed().thenComparing(ContestRankVo::getUid)
                : Comparator.comparingInt(ContestRankVo::getSolved).reversed()
                        .thenComparingLong(ContestRankVo::getPenaltyMinutes).thenComparing(ContestRankVo::getUid));
        for (int i = 0; i < rows.size(); i++) {
            ContestRankVo current = rows.get(i);
            ContestRankVo previous = i == 0 ? null : rows.get(i - 1);
            boolean tied = previous != null && (oi
                    ? current.getTotalScore() == previous.getTotalScore()
                    : current.getSolved() == previous.getSolved()
                            && current.getPenaltyMinutes() == previous.getPenaltyMinutes());
            current.setRank(tied ? previous.getRank() : i + 1);
        }
        return rows;
    }

    private static class Participant {
        final String uid;
        String username;
        final Map<Long, ProblemResult> problems = new HashMap<>();
        Participant(String uid, String username) { this.uid = uid; this.username = username; }
    }

    private static class ProblemResult {
        int wrongAttempts;
        boolean accepted;
        int score;
        long penaltyMinutes;
    }
}
