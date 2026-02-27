package com.hnieacm.user.service.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.dto.UserContextDto;
import com.hnieacm.user.entity.SysClass;
import com.hnieacm.user.entity.SysCollege;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.SysCollegeMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 用户基础信息管理
 */
@Component
@RequiredArgsConstructor
public class UserInfoManager {

    private final UserInfoMapper userInfoMapper;
    private final SysCollegeMapper sysCollegeMapper;
    private final SysClassMapper sysClassMapper;

    /**
     * @MethodName getUserByUid
     * @Param uid
     * @Description 按 uid 获取用户
     * @Return @return {@link UserInfo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/27
     */
    public UserInfo getUserByUid(String uid) {
        UserInfo user = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUid, uid));
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /**
     * @MethodName getUserContextByUid
     * @Param uid
     * @Description 获取用户及学院班级上下文
     * @Return @return {@link UserContextDto }
     * @Author HaoRan_Lyu
     * @Date 2026/02/27
     */
    public UserContextDto getUserContextByUid(String uid) {
        UserInfo user = getUserByUid(uid);
        SysCollege college = null;
        if (user.getCollegeId() != null) {
            college = sysCollegeMapper.selectById(user.getCollegeId());
        }
        SysClass sysClass = null;
        if (user.getClassId() != null) {
            sysClass = sysClassMapper.selectById(user.getClassId());
        }
        return new UserContextDto(user, college, sysClass);
    }
}
