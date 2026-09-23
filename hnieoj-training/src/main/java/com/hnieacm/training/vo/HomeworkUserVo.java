package com.hnieacm.training.vo;

import lombok.Data;

/**
 * Fields needed to check whether the submitting user belongs to a homework class.
 * @author HnieOJ contributors
 */
@Data
public class HomeworkUserVo {
    private String uid;
    private Long classId;
}
