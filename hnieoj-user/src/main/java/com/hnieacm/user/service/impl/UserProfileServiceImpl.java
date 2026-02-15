package com.hnieacm.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.entity.Role;
import com.hnieacm.user.entity.SysClass;
import com.hnieacm.user.entity.SysCollege;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.entity.UserRole;
import com.hnieacm.user.mapper.RoleMapper;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.SysCollegeMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserRoleMapper;
import com.hnieacm.user.service.UserProfileService;
import com.hnieacm.user.vo.UserProfileVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 用户信息服务实现
 */
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserInfoMapper userInfoMapper;
    private final SysCollegeMapper sysCollegeMapper;
    private final SysClassMapper sysClassMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    /**
     * @MethodName getCurrentUserProfile
     * <p></p>
     * @Description 获取当前登录用户信息
     * @Return @return {@link UserProfileVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Override
    public UserProfileVo getCurrentUserProfile() {
        String uid = StpUtil.getLoginIdAsString();

        UserInfo user = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUid, uid)
        );
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }

        SysCollege college = null;
        if (user.getCollegeId() != null) {
            college = sysCollegeMapper.selectById(user.getCollegeId());
        }

        SysClass sysClass = null;
        if (user.getClassId() != null) {
            sysClass = sysClassMapper.selectById(user.getClassId());
        }

        List<String> roles = getUserRoles(uid);
        if (roles.isEmpty()) {
            roles = List.of(RoleConstant.STUDENT);
        }

        UserProfileVo vo = new UserProfileVo();
        vo.setUid(user.getUid());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setQq(user.getQq());
        vo.setGrade(user.getGrade());
        vo.setRealname(user.getRealname());
        vo.setCfUsername(user.getCfUsername());
        vo.setGithub(user.getGithub());
        vo.setBlog(user.getBlog());
        vo.setRoles(roles);
        vo.setCollege(college == null ? null : college.getName());
        vo.setClassName(sysClass == null ? null : sysClass.getName());
        return vo;
    }

    /**
     * @MethodName getUserRoles
     * @Param uid
     * @Description 获取登录用户角色
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private List<String> getUserRoles(String uid) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserUid, uid)
        );
        if (userRoles == null || userRoles.isEmpty()) {
            return List.of();
        }

        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }

        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }

        return roles.stream()
                .sorted(Comparator.comparing(Role::getId))
                .map(Role::getRole)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
