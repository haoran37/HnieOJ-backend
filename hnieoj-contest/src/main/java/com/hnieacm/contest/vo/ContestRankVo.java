package com.hnieacm.contest.vo;

import lombok.Data;

import java.util.Map;

/**
 * @author HnieOJ contributors
 */
@Data
public class ContestRankVo {
    private int rank;
    private String uid;
    private String username;
    private int solved;
    private int totalScore;
    private long penaltyMinutes;
    private Map<Long, Integer> problemScores;
}
