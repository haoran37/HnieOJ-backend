package com.hnieacm.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.constant.AuthCacheConstant;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.dto.UpdatePasswordRequest;
import com.hnieacm.user.dto.UpdateUserProfileRequest;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

    private static final String QQ_REGEX = "\\d{5,11}";

    private static final String SCHEME_HTTP = "http";

    private static final String SCHEME_HTTPS = "https";

    private static final int MAX_AVATAR_LENGTH = 500;

    private static final int MAX_EXTERNAL_URL_LENGTH = 255;

    private final UserInfoManager userInfoManager;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final UserInfoMapper userInfoMapper;
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
    public UserProfileVo getCurrentUserProfile(String uid) {
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
        vo.setCollegeId(user.getCollegeId());
        vo.setClassName(sysClass == null ? null : sysClass.getName());
        vo.setClassId(user.getClassId());
        return vo;
    }

    /**
     * @MethodName updateCurrentUserProfile
     * @Description 本人自助修改普通资料：字段白名单 + “null 未传保留、空串清除”。
     * <p>
     * 只通过 {@link LambdaUpdateWrapper} 更新请求中显式提供的白名单列，绝不整行回写：
     * 整行回写会在 MyBatis-Plus 默认 NOT_NULL 策略下丢掉空串清除的 null，也可能把旧快照里的
     * password/身份字段覆盖回去。空串清除通过 {@code set(column, null)} 生成 {@code SET column=NULL}，
     * WHERE 使用服务端登录态 uid，保证只改本人这一行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentUserProfile(String uid, UpdateUserProfileRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        if (StrUtil.isBlank(uid)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录或登录已过期");
        }

        LambdaUpdateWrapper<UserInfo> wrapper = new LambdaUpdateWrapper<>();
        boolean changed = false;

        if (request.getUsername() != null) {
            String username = request.getUsername().trim();
            if (username.isEmpty()) {
                throw new BizException(ResultCode.BAD_REQUEST, "username 不能为空");
            }
            validateUsernameLength(username);
            wrapper.set(UserInfo::getUsername, username);
            changed = true;
        }

        if (request.getAvatar() != null) {
            wrapper.set(UserInfo::getAvatar, normalizeAvatar(request.getAvatar()));
            changed = true;
        }
        if (request.getQq() != null) {
            wrapper.set(UserInfo::getQq, normalizeQq(request.getQq()));
            changed = true;
        }
        if (request.getGithub() != null) {
            wrapper.set(UserInfo::getGithub, normalizeExternalUrl("github", request.getGithub()));
            changed = true;
        }
        if (request.getBlog() != null) {
            wrapper.set(UserInfo::getBlog, normalizeExternalUrl("blog", request.getBlog()));
            changed = true;
        }

        if (!changed) {
            // 全部字段为 null（未传）：不做任何写操作，保留原值。
            return;
        }

        wrapper.eq(UserInfo::getUid, uid);
        userInfoMapper.update(null, wrapper);
    }

    /**
     * @MethodName updatePassword
     * @Description 本人自助修改密码；旧密码错误不写库、不踢下线，成功后事务提交后才失效会话。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(String uid, UpdatePasswordRequest request) {
        if (request == null || StrUtil.isBlank(request.getOldPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "旧密码不能为空");
        }
        if (StrUtil.isBlank(request.getNewPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码不能为空");
        }
        validatePasswordLength(request.getNewPassword());

        // 事务内锁定用户行，避免并发改密相互覆盖。
        UserInfo user = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUid, uid).last("FOR UPDATE")
        );
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }

        if (!BCrypt.checkpw(request.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PASSWORD_ERROR, "旧密码错误");
        }

        user.setPassword(BCrypt.hashpw(request.getNewPassword()));
        userInfoMapper.updateById(user);

        // 密码已变更：事务提交后失效该用户全部会话，避免未提交就踢下线。
        runAfterCommit(() -> invalidateSessions(uid));
    }

    /**
     * 事务提交后失效用户全部会话（含清理鉴权缓存）。包级可见以便单元测试在不启动 Sa-Token 上下文时替换。
     */
    void invalidateSessions(String uid) {
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

    /**
     * 事务内登记 afterCommit 回调；无事务时立即执行。
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    /**
     * avatar 允许 http/https 绝对地址或站内相对路径（以单个 "/" 开头），拒绝 javascript: 等危险 scheme。
     */
    private String normalizeAvatar(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > MAX_AVATAR_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "avatar 长度不能超过 " + MAX_AVATAR_LENGTH);
        }
        ParsedUrl parsed = parseUrl(value);
        if (parsed.absolute()) {
            checkExternalScheme(parsed.scheme());
            return value;
        }
        if (!value.startsWith("/") || value.startsWith("//")) {
            throw new BizException(ResultCode.BAD_REQUEST, "avatar 仅支持 http/https 或站内路径");
        }
        return value;
    }

    private String normalizeExternalUrl(String field, String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > MAX_EXTERNAL_URL_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    field + " 长度不能超过 " + MAX_EXTERNAL_URL_LENGTH);
        }
        ParsedUrl parsed = parseUrl(value);
        if (!parsed.absolute()) {
            throw new BizException(ResultCode.BAD_REQUEST, field + " 仅支持 http/https 链接");
        }
        checkExternalScheme(parsed.scheme());
        return value;
    }

    private String normalizeQq(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!Validator.isMatchRegex(QQ_REGEX, value)) {
            throw new BizException(ResultCode.INVALID_QQ, "QQ号格式不正确");
        }
        return value;
    }

    private ParsedUrl parseUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            if (uri.isAbsolute()) {
                if (uri.getHost() == null || uri.getHost().isBlank()) {
                    throw new BizException(ResultCode.BAD_REQUEST, "URL 格式不正确");
                }
                return new ParsedUrl(true, scheme);
            }
            return new ParsedUrl(false, null);
        } catch (URISyntaxException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "URL 格式不正确");
        }
    }

    private void checkExternalScheme(String scheme) {
        if (!SCHEME_HTTP.equals(scheme) && !SCHEME_HTTPS.equals(scheme)) {
            throw new BizException(ResultCode.BAD_REQUEST, "URL 仅支持 http/https");
        }
    }

    private void validateUsernameLength(String username) {
        int minLength = userManageProperties.getUsernameMinLength();
        int maxLength = userManageProperties.getUsernameMaxLength();
        if (username.length() < minLength || username.length() > maxLength) {
            throw new BizException(ResultCode.INVALID_USERNAME,
                    "用户名长度应在" + minLength + "-" + maxLength + "之间");
        }
    }

    private void validatePasswordLength(String password) {
        int minLength = userManageProperties.getPasswordMinLength();
        int maxLength = userManageProperties.getPasswordMaxLength();
        if (password.length() < minLength || password.length() > maxLength) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "密码长度应在" + minLength + "-" + maxLength + "之间");
        }
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

    private record ParsedUrl(boolean absolute, String scheme) {
    }
}
