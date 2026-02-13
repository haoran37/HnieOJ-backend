package com.hnieacm.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.auth.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/11
 * @Description: 用户信息Mapper
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}
