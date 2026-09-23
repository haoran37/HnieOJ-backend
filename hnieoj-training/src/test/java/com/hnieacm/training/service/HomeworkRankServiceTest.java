package com.hnieacm.training.service;

import com.hnieacm.common.dto.HomeworkBestScoreVo;
import com.hnieacm.training.vo.HomeworkRankVo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeworkRankServiceTest {
    @Test
    void ranksAggregatedScores() {
        List<HomeworkRankVo> rows = HomeworkRankService.calculate(List.of(
                score("alice", 80), score("bob", 60)));
        assertEquals("alice", rows.get(0).getUid());
        assertEquals(80, rows.get(0).getTotalScore());
        assertEquals(0, rows.get(0).getSolved());
    }

    private static HomeworkBestScoreVo score(String uid, int value) {
        HomeworkBestScoreVo row = new HomeworkBestScoreVo();
        row.setUid(uid);
        row.setUsername(uid);
        row.setProblemId(1L);
        row.setBestScore(value);
        return row;
    }
}
