package com.hnieacm.training.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 管理端题单完整详情展示对象（含停用/私有题单的可编辑字段）
 */
@Data
public class AdminTrainingDetailVo {

    private Long id;

    private String title;

    private String type;

    private String auth;

    private String privatePwd;

    private String description;

    private Boolean status;

    private Integer rank;

    private List<Problem> problems;

    /**
     * 题单题目条目：仅暴露编辑所需的 problemId 与 displayId
     */
    @Data
    public static class Problem {

        private Long problemId;

        private Integer displayId;
    }
}
