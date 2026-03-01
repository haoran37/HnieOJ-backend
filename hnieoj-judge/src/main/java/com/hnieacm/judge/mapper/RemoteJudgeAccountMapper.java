package com.hnieacm.judge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.judge.entity.RemoteJudgeAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 远程评测账号 Mapper
 */
@Mapper
public interface RemoteJudgeAccountMapper extends BaseMapper<RemoteJudgeAccount> {
}
