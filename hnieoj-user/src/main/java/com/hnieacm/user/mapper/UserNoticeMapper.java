package com.hnieacm.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.user.entity.UserNotice;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 定向通知 Mapper
 */
@Mapper
public interface UserNoticeMapper extends BaseMapper<UserNotice> {
}
