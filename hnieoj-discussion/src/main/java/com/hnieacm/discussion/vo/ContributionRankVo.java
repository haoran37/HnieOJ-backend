package com.hnieacm.discussion.vo;

import lombok.Data;

/**
 * @author HnieOJ contributors
 */
@Data
public class ContributionRankVo {
    private int rank;
    private String uid;
    private String username;
    private int contribution;
    private int posts;
    private int answers;
}
