package com.hnieacm.problem.vo;

import com.hnieacm.problem.dto.ProblemRequest;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 管理端题目完整编辑详情：复用 {@link ProblemRequest} 承载全部可编辑字段，
 * 使前端能可靠回填 SPJ/交互题配置等公共详情不返回的字段，并附带题目标签。
 */
@Data
public class AdminProblemDetailVo {

    private ProblemRequest problem;

    private List<String> tags;
}
