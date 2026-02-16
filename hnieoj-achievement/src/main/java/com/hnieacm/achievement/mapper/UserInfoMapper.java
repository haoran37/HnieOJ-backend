package com.hnieacm.achievement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.achievement.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 用户信息 Mapper（用于校验用户是否存在）
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}

