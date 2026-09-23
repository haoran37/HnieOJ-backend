package com.hnieacm.submission.vo;

import lombok.Data;

/**
 * @author HnieOJ contributors
 */
@Data
public class UserProblemSummaryVo {
    private String problemCode;
    private Boolean accepted;
    private Integer attempts;
}
