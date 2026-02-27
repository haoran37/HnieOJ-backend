package com.hnieacm.user.dto;

import com.hnieacm.user.entity.SysClass;
import com.hnieacm.user.entity.SysCollege;
import com.hnieacm.user.entity.UserInfo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 用户上下文数据
 */
public record UserContextDto(UserInfo user, SysCollege college, SysClass sysClass) {
}
