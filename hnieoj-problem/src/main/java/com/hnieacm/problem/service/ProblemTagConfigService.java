package com.hnieacm.problem.service;

import com.hnieacm.problem.dto.SaveTagConfigRequest;
import com.hnieacm.problem.vo.TagGroupVo;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 题目标签配置服务
 */
public interface ProblemTagConfigService {

    List<TagGroupVo> list();

    void save(SaveTagConfigRequest request);
}
