package com.hnieacm.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.user.entity.UserProfileChange;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 用户身份资料变更申请 Mapper
 */
@Mapper
public interface UserProfileChangeMapper extends BaseMapper<UserProfileChange> {
}
