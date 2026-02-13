package com.hnieacm.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.auth.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: 用户-角色关联 Mapper
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}

