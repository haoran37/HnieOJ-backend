package com.hnieacm.submission.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * @author HnieOJ contributors
 */
@Data
public class UserDailySubmitVo {
    private LocalDate day;
    private Integer submissions;
    private Integer accepted;
}
