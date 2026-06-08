package com.hnieacm.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.hnieacm.common.constant.AuthCacheConstant;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.dto.ChangeCurrentPasswordRequest;
import com.hnieacm.user.dto.UserContextDto;
import com.hnieacm.user.entity.Role;
import com.hnieacm.user.entity.SysClass;
import com.hnieacm.user.entity.SysCollege;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.entity.UserRole;
import com.hnieacm.user.mapper.RoleMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserRoleMapper;
import com.hnieacm.user.properties.UserManageProperties;
import com.hnieacm.user.service.UserProfileService;
import com.hnieacm.user.service.manager.UserInfoManager;
import com.hnieacm.user.vo.UserProfileVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 用户信息服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserInfoManager userInfoManager;
    private final UserInfoMapper userInfoMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final UserManageProperties userManageProperties;
    private final StringRedisTemplate stringRedisTemplate;

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

        UserContextDto userContext = userInfoManager.getUserContextByUid(uid);
        UserInfo user = userContext.user();
        SysCollege college = userContext.college();
        SysClass sysClass = userContext.sysClass();

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

    @Override
    public void changeCurrentPassword(ChangeCurrentPasswordRequest request) {
        if (request == null || StrUtil.isBlank(request.getOldPassword()) || StrUtil.isBlank(request.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "oldPassword 和 password 不能为空");
        }
        validatePasswordLength(request.getPassword());

        String uid = StpUtil.getLoginIdAsString();
        UserInfo user = userInfoManager.getUserByUid(uid);
        if (!BCrypt.checkpw(request.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "旧密码错误");
        }

        user.setPassword(BCrypt.hashpw(request.getPassword()));
        user.setPasswordResetRequired(false);
        userInfoMapper.updateById(user);
        kickoutUserSafely(uid);
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

    private void validatePasswordLength(String password) {
        int minLength = userManageProperties.getPasswordMinLength();
        int maxLength = userManageProperties.getPasswordMaxLength();
        if (password.length() < minLength || password.length() > maxLength) {
            throw new BizException(ResultCode.BAD_REQUEST, "password 长度应在 " + minLength + "-" + maxLength + " 之间");
        }
    }

    private void kickoutUserSafely(String uid) {
        try {
            List<String> tokenValues = StpUtil.getTokenValueListByLoginId(uid);
            if (tokenValues != null && !tokenValues.isEmpty()) {
                for (String tokenValue : tokenValues) {
                    if (StrUtil.isNotBlank(tokenValue)) {
                        StpUtil.logoutByTokenValue(tokenValue.trim());
                    }
                }
            }
            StpUtil.logout(uid);
            StpUtil.kickout(uid);
        } catch (Exception e) {
            log.debug("Kickout ignored, uid: {}, msg: {}", uid, e.getMessage());
        } finally {
            deleteUserAuthCache(uid);
        }
    }

    private void deleteUserAuthCache(String uid) {
        if (StrUtil.isBlank(uid)) {
            return;
        }
        try {
            stringRedisTemplate.delete(AuthCacheConstant.ROLE_CACHE_PREFIX + uid);
            stringRedisTemplate.delete(AuthCacheConstant.PERMISSION_CACHE_PREFIX + uid);
        } catch (Exception e) {
            log.debug("Delete auth cache ignored, uid: {}, msg: {}", uid, e.getMessage());
        }
    }
}
