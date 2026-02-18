package com.hnieacm.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/17
 * @Description: 年级信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GradeVo {

    /**
     * 前端排序用的自增序号（从 1 开始）
     */
    private Integer id;

    private String grade;
}
