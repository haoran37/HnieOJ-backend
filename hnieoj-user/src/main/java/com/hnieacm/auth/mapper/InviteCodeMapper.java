package com.hnieacm.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.auth.entity.InviteCode;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: HnieOJ contributors
 * @Description: Registration invitation persistence.
 */
@Mapper
public interface InviteCodeMapper extends BaseMapper<InviteCode> {
}
