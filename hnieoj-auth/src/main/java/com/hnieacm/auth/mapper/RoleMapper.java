package com.hnieacm.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.auth.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: 角色 Mapper
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}

