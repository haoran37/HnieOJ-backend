package com.hnieacm.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.auth.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: 权限 Mapper
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}

