package com.hnieacm.problem.service;

import java.io.OutputStream;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 题目导出服务
 */
public interface ProblemExportService {

    void exportProblems(List<Long> ids, OutputStream outputStream);
}
