package com.hnieacm.common.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Query window and problem scope for homework score aggregation.
 * @param homeworkId homework identifier
 * @param startTime opening time
 * @param endTime closing time
 * @param problemIds problems assigned to the homework
 * @author HnieOJ contributors
 */
public record HomeworkScoreQuery(Long homeworkId, LocalDateTime startTime, LocalDateTime endTime,
                                 List<Long> problemIds) { }
