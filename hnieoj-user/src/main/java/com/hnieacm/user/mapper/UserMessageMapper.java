package com.hnieacm.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.user.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 站内消息 Mapper
 */
@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {
}
