package com.hnieacm.judge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.judge.entity.Judge;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/06
 * @Description: 提交记录 Mapper
 */
@Mapper
public interface JudgeMapper extends BaseMapper<Judge> {
}
