package com.hnieacm.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.problem.entity.Problem;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: Problem mapper
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {
}

