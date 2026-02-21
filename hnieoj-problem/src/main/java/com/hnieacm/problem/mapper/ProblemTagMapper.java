package com.hnieacm.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.problem.entity.ProblemTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: Problem-tag relation mapper
 */
@Mapper
public interface ProblemTagMapper extends BaseMapper<ProblemTag> {
}

