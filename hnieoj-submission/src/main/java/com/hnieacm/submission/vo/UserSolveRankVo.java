package com.hnieacm.submission.vo;

import lombok.Data;

/**
 * @author HnieOJ contributors
 */
@Data
public class UserSolveRankVo {
    private int rank;
    private String uid;
    private String username;
    private int solved;
    private int monthlySolved;
    private int submissions;
}
