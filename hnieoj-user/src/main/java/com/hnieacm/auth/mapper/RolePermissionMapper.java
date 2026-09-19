package com.hnieacm.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.auth.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: 角色-权限关联 Mapper
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}

