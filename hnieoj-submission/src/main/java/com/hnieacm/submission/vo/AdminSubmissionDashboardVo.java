package com.hnieacm.submission.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * @author HnieOJ contributors
 */
@Data
public class AdminSubmissionDashboardVo {
    private LocalDate reportDate;
    private long totalSubmissions;
    private List<Daily> daily;
    private List<Status> statuses;
    private List<Problem> hotProblems;
    private List<Problem> lowActivityProblems;

    @Data public static class Daily {
        private LocalDate day;
        private int submissions;
    }
    @Data public static class Status {
        private int status;
        private int submissions;
    }
    @Data public static class Problem {
        private String problemCode;
        private int submissions;
    }
}
