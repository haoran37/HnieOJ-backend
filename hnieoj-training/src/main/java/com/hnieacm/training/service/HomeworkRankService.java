package com.hnieacm.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.dto.HomeworkBestScoreVo;
import com.hnieacm.common.dto.HomeworkScoreQuery;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.training.feign.ScoreSubmissionFeignClient;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.training.entity.Homework;
import com.hnieacm.training.entity.HomeworkProblem;
import com.hnieacm.training.mapper.HomeworkMapper;
import com.hnieacm.training.mapper.HomeworkProblemMapper;
import com.hnieacm.training.vo.HomeworkRankVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author HnieOJ contributors
 */
@Service
@RequiredArgsConstructor
public class HomeworkRankService {
    private final HomeworkMapper homeworkMapper;
    private final HomeworkProblemMapper problemMapper;
    private final ScoreSubmissionFeignClient submissionClient;

    public List<HomeworkRankVo> standings(long homeworkId) {
        Homework homework = homeworkMapper.selectById(homeworkId);
        if (homework == null || !Integer.valueOf(1).equals(homework.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "作业不存在或不可访问");
        }
        Set<Long> problemIds = problemMapper.selectList(new LambdaQueryWrapper<HomeworkProblem>()
                .eq(HomeworkProblem::getHid, homeworkId)).stream()
                .map(HomeworkProblem::getProblemId).collect(Collectors.toSet());
        if (problemIds.isEmpty()) {
            return List.of();
        }
        Result<List<HomeworkBestScoreVo>> response = submissionClient.listHomeworkBestScores(
                new HomeworkScoreQuery(homeworkId, homework.getStartTime(), homework.getEndTime(),
                        List.copyOf(problemIds)));
        if (response == null || response.getCode() != ResultCode.SUCCESS || response.getData() == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "作业成绩暂不可用");
        }
        return calculate(response.getData());
    }

    static List<HomeworkRankVo> calculate(List<HomeworkBestScoreVo> submissions) {
        Map<String, Map<Long, Integer>> scores = new HashMap<>(16);
        Map<String, String> names = new HashMap<>(16);
        for (HomeworkBestScoreVo submission : submissions) {
            if (submission.getUid() == null || submission.getProblemId() == null
                    || submission.getBestScore() == null) {
                continue;
            }
            names.put(submission.getUid(), submission.getUsername());
            scores.computeIfAbsent(submission.getUid(), ignored -> new HashMap<>(16))
                    .merge(submission.getProblemId(), submission.getBestScore(), Math::max);
        }
        List<HomeworkRankVo> rows = new ArrayList<>();
        scores.forEach((uid, byProblem) -> {
            HomeworkRankVo row = new HomeworkRankVo();
            row.setUid(uid);
            row.setUsername(names.get(uid));
            row.setTotalScore(byProblem.values().stream().mapToInt(Integer::intValue).sum());
            row.setSolved((int) byProblem.values().stream().filter(score -> score == 100).count());
            rows.add(row);
        });
        rows.sort(Comparator.comparingInt(HomeworkRankVo::getTotalScore).reversed()
                .thenComparing(Comparator.comparingInt(HomeworkRankVo::getSolved).reversed())
                .thenComparing(HomeworkRankVo::getUid));
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setRank(i + 1);
        }
        return rows;
    }
}
