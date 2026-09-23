package com.hnieacm.training.vo;

import lombok.Data;

/**
 * @author HnieOJ contributors
 */
@Data
public class HomeworkRankVo {
    private int rank;
    private String uid;
    private String username;
    private int solved;
    private int totalScore;
}
