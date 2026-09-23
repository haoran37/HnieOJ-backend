package com.hnieacm.common.dto;

import lombok.Data;

/** Highest score for one user and one homework problem.
 * @author HnieOJ contributors
 */
@Data
public class HomeworkBestScoreVo {
    private Long problemId;
    private String uid;
    private String username;
    private Integer bestScore;
}
