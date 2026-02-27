package com.hnieacm.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.common.constant.AuthCacheConstant;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.constant.RoleIdConstant;
import com.hnieacm.common.constant.UserStatusConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.dto.BatchUidsRequest;
import com.hnieacm.user.dto.CreateUserRequest;
import com.hnieacm.user.dto.GrantPermissionRequest;
import com.hnieacm.user.dto.UpdateUserPasswordRequest;
import com.hnieacm.user.dto.UpdateUserPermissionRequest;
import com.hnieacm.user.dto.UpdateUserRequest;
import com.hnieacm.user.dto.UserContextDto;
import com.hnieacm.user.entity.Role;
import com.hnieacm.user.entity.SysClass;
import com.hnieacm.user.entity.SysCollege;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.entity.UserRole;
import com.hnieacm.user.feign.AuthInternalFeignClient;
import com.hnieacm.user.mapper.RoleMapper;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.SysCollegeMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserRoleMapper;
import com.hnieacm.user.properties.UserManageProperties;
import com.hnieacm.user.service.UserManageService;
import com.hnieacm.user.service.manager.UserInfoManager;
import com.hnieacm.user.vo.CreateUserVo;
import com.hnieacm.user.vo.PermissionUserVo;
import com.hnieacm.user.vo.UserDetailVo;
import com.hnieacm.user.vo.UserListVo;
import com.hnieacm.user.vo.UserSearchVo;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 用户管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManageServiceImpl implements UserManageService {

    private final UserInfoMapper userInfoMapper;
    private final SysCollegeMapper sysCollegeMapper;
    private final SysClassMapper sysClassMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final AuthInternalFeignClient authInternalFeignClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserManageProperties userManageProperties;
    private final UserInfoManager userInfoManager;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @MethodName createUser
     * @Param request
     * @Description 创建用户
     * @Return @return {@link CreateUserVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateUserVo createUser(CreateUserRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }

        String uid = StrUtil.trim(request.getUid());
        if (StrUtil.isBlank(uid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }

        long userCount = userInfoMapper.selectCount(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUid, uid));
        if (userCount > 0) {
            throw new BizException(ResultCode.USER_ALREADY_EXISTS, "该uid已存在");
        }

        validateUsernameLength(request.getUsername());

        Long collegeId = resolveCollegeId(request.getCollegeId());
        SysClass sysClass = resolveClassId(request.getClassId(), collegeId);

        String grade = request.getGrade();
        if (StrUtil.isBlank(grade) && sysClass != null) {
            grade = sysClass.getGrade();
        }

        String plainPassword = request.getPassword();
        String initialPassword = null;
        if (StrUtil.isBlank(plainPassword)) {
            initialPassword = generatePassword();
            plainPassword = initialPassword;
        }
        validatePasswordLength(plainPassword);

        UserInfo user = new UserInfo();
        user.setUuid(generateUuid32());
        user.setUid(uid);
        user.setUsername(request.getUsername());
        user.setPassword(BCrypt.hashpw(plainPassword));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setAvatar(request.getAvatar());
        user.setCollegeId(collegeId);
        user.setClassId(sysClass == null ? null : sysClass.getId());
        user.setGrade(grade);
        user.setStatus(UserStatusConstant.NORMAL);

        try {
            userInfoMapper.insert(user);
        } catch (DuplicateKeyException e) {
            Long uidExists = userInfoMapper.selectCount(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUid, uid));
            if (uidExists != null && uidExists > 0) {
                throw new BizException(ResultCode.USER_ALREADY_EXISTS, "该uid已存在");
            }
            Long emailExists = userInfoMapper.selectCount(
                    new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getEmail, request.getEmail())
            );
            if (emailExists != null && emailExists > 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "邮箱已被占用");
            }
            throw new BizException(ResultCode.INTERNAL_ERROR, "创建用户失败，请稍后重试");
        }

        UserRole userRole = new UserRole();
        userRole.setUserUid(uid);
        userRole.setRoleId(RoleIdConstant.STUDENT);
        try {
            userRoleMapper.insert(userRole);
        } catch (DuplicateKeyException e) {
            log.warn("Insert student role duplicated, uid: {}", uid);
        }

        return new CreateUserVo(uid, initialPassword);
    }

    /**
     * @MethodName updateUser
     * @Param uid
     * @Param uid
     * @Description 更新用户
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(String uid, UpdateUserRequest request) {
        if (StrUtil.isBlank(uid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }

        UserInfo user = userInfoManager.getUserByUid(uid);

        if (StrUtil.isNotBlank(request.getUsername())) {
            validateUsernameLength(request.getUsername());
            user.setUsername(request.getUsername());
        }

        if (StrUtil.isNotBlank(request.getEmail()) && !Objects.equals(request.getEmail(), user.getEmail())) {
            long emailCount = userInfoMapper.selectCount(
                    new LambdaQueryWrapper<UserInfo>()
                            .eq(UserInfo::getEmail, request.getEmail())
                            .ne(UserInfo::getUid, uid)
            );
            if (emailCount > 0) {
                throw new BizException(ResultCode.USER_ALREADY_EXISTS, "邮箱已被占用");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }

        Long collegeId = resolveCollegeId(request.getCollegeId());
        SysClass sysClass = resolveClassId(request.getClassId(), collegeId);

        if (collegeId != null) {
            user.setCollegeId(collegeId);
        }
        if (sysClass != null) {
            user.setClassId(sysClass.getId());
        }
        if (StrUtil.isNotBlank(request.getGrade())) {
            user.setGrade(request.getGrade());
        } else if (sysClass != null && StrUtil.isNotBlank(sysClass.getGrade())) {
            // 当更换班级但未显式传 grade 时，使用班级表中的 grade
            user.setGrade(sysClass.getGrade());
        }

        Integer status = resolveStatus(request.getStatus());
        boolean needKickout = false;
        if (status != null) {
            user.setStatus(status);
            needKickout = status == UserStatusConstant.DISABLED;
        }

        userInfoMapper.updateById(user);

        if (needKickout) {
            kickoutUserSafely(uid);
        }
    }

    /**
     * @MethodName updateUserPassword
     * @Param uid
     * @Param uid
     * @Description 更新用户密码
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserPassword(String uid, UpdateUserPasswordRequest request) {
        if (StrUtil.isBlank(uid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }
        if (request == null || StrUtil.isBlank(request.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "password 不能为空");
        }
        validatePasswordLength(request.getPassword());

        UserInfo user = userInfoManager.getUserByUid(uid);

        user.setPassword(BCrypt.hashpw(request.getPassword()));
        userInfoMapper.updateById(user);

        // 重置密码后强制下线
        kickoutUserSafely(uid);
    }

    /**
     * @MethodName deleteUser
     * @Param uid
     * @Description 删除用户
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(String uid) {
        if (StrUtil.isBlank(uid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }

        UserInfo user = userInfoManager.getUserByUid(uid);

        // 先下线
        kickoutUserSafely(uid);

        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserUid, uid));
        userInfoMapper.deleteById(user.getUuid());

        // 删除用户后角色/权限缓存无意义：直接清理缓存
        deleteUserAuthCache(uid);
    }

    /**
     * @MethodName batchDisableUsers
     * @Param request
     * @Description 批量禁用用户
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDisableUsers(BatchUidsRequest request) {
        List<String> uids = normalizeUids(request);
        if (uids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "uids 不能为空");
        }

        userInfoMapper.update(
                null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserInfo>()
                        .in(UserInfo::getUid, uids)
                        .set(UserInfo::getStatus, UserStatusConstant.DISABLED)
        );

        uids.forEach(this::kickoutUserSafely);
    }

    /**
     * @MethodName batchEnableUsers
     * @Param request
     * @Description 批量启用用户
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchEnableUsers(BatchUidsRequest request) {
        List<String> uids = normalizeUids(request);
        if (uids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "uids 不能为空");
        }

        userInfoMapper.update(
                null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserInfo>()
                        .in(UserInfo::getUid, uids)
                        .set(UserInfo::getStatus, UserStatusConstant.NORMAL)
        );
    }

    /**
     * @MethodName batchDeleteUsers
     * @Param request
     * @Description 批量删除用户
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteUsers(BatchUidsRequest request) {
        List<String> uids = normalizeUids(request);
        if (uids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "uids 不能为空");
        }

        // 先下线
        uids.forEach(this::kickoutUserSafely);

        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().in(UserRole::getUserUid, uids));
        userInfoMapper.delete(new LambdaQueryWrapper<UserInfo>().in(UserInfo::getUid, uids));

        uids.forEach(this::deleteUserAuthCache);
    }


    @Override
    public PageVo<UserListVo> listUsers(String keyword, Long collegeId, String grade, Long classId, int page, int pageSize) {
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 和 pageSize 必须大于 0");
        }

        String normalizedKeyword = StrUtil.trimToNull(keyword);
        String normalizedGrade = StrUtil.trimToNull(grade);

        if (normalizedGrade != null && collegeId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "填写 grade 时必须同时填写 collegeId");
        }

        if (collegeId != null) {
            SysCollege college = sysCollegeMapper.selectById(collegeId);
            if (college == null) {
                throw new BizException(ResultCode.COLLEGE_NOT_FOUND, "学院不存在");
            }
        }

        if (normalizedGrade != null) {
            Long gradeCount = sysClassMapper.selectCount(
                    new LambdaQueryWrapper<SysClass>()
                            .eq(SysClass::getCollegeId, collegeId)
                            .eq(SysClass::getGrade, normalizedGrade)
            );
            if (gradeCount == null || gradeCount == 0) {
                throw new BizException(ResultCode.GRADE_NOT_FOUND, "年级不存在");
            }
        }

        if (classId != null) {
            if (collegeId == null || normalizedGrade == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "填写 classId 时必须同时填写 collegeId 和 grade");
            }
            SysClass sysClass = sysClassMapper.selectById(classId);
            if (sysClass == null) {
                throw new BizException(ResultCode.CLASS_NOT_FOUND, "班级不存在");
            }
            if (!Objects.equals(sysClass.getCollegeId(), collegeId)) {
                throw new BizException(ResultCode.BAD_REQUEST, "班级不属于指定学院");
            }
            if (!Objects.equals(sysClass.getGrade(), normalizedGrade)) {
                throw new BizException(ResultCode.BAD_REQUEST, "班级不属于指定年级");
            }
        }

        Page<UserListVo> mpPage = new Page<>(page, pageSize);
        Page<UserListVo> result = (Page<UserListVo>) userInfoMapper.selectUserList(
                mpPage, normalizedKeyword, collegeId, normalizedGrade, classId
        );
        fillRolesForUserList(result.getRecords());
        return new PageVo<>(result.getRecords(), result.getTotal());
    }

    @Override
    public UserDetailVo getUserDetail(String uid) {
        uid = StrUtil.trim(uid);
        if (StrUtil.isBlank(uid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }

        UserContextDto userContext = userInfoManager.getUserContextByUid(uid);
        UserInfo user = userContext.user();
        SysCollege college = userContext.college();
        SysClass sysClass = userContext.sysClass();

        List<String> roles = queryUserRolesMap(List.of(uid)).getOrDefault(uid, Collections.emptyList());
        if (roles.isEmpty()) {
            roles = List.of(RoleConstant.STUDENT);
        }

        UserDetailVo vo = new UserDetailVo();
        vo.setUid(user.getUid());
        vo.setUsername(user.getUsername());
        vo.setRealname(user.getRealname());
        vo.setAvatar(user.getAvatar());
        vo.setCollegeId(user.getCollegeId());
        vo.setCollege(college == null ? null : college.getName());
        vo.setGrade(user.getGrade());
        vo.setClassId(user.getClassId());
        vo.setMajorClass(sysClass == null ? null : sysClass.getName());
        vo.setCfUsername(user.getCfUsername());
        vo.setGithub(user.getGithub());
        vo.setBlog(user.getBlog());
        vo.setRoles(roles);
        return vo;
    }

    /**
     * @MethodName getPermissionUsers
     * @Param page
     * @Param page
     * @Description 获取权限用户
     * @Return @return {@link PageVo }<{@link PermissionUserVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Override
    public PageVo<PermissionUserVo> getPermissionUsers(int page, int pageSize) {
        if (userManageProperties.getManageableRoleIds() == null || userManageProperties.getManageableRoleIds().isEmpty()) {
            return new PageVo<>(Collections.emptyList(), 0);
        }

        Page<PermissionUserVo> mpPage = new Page<>(page, pageSize);
        Page<PermissionUserVo> result =
                (Page<PermissionUserVo>) userInfoMapper.selectPermissionUsers(mpPage, userManageProperties.getManageableRoleIds());
        fillRolesForPermissionUsers(result.getRecords());
        return new PageVo<>(result.getRecords(), result.getTotal());
    }

    /**
     * @MethodName searchUsers
     * @Param query
     * @Param query
     * @Param query
     * @Description 搜索用户
     * @Return @return {@link PageVo }<{@link UserSearchVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Override
    public PageVo<UserSearchVo> searchUsers(String query, int page, int pageSize) {
        if (StrUtil.isBlank(query)) {
            throw new BizException(ResultCode.BAD_REQUEST, "query 不能为空");
        }
        Page<UserSearchVo> mpPage = new Page<>(page, pageSize);
        Page<UserSearchVo> result = (Page<UserSearchVo>) userInfoMapper.searchUsers(mpPage, query.trim());
        fillRolesForSearchedUsers(result.getRecords());
        return new PageVo<>(result.getRecords(), result.getTotal());
    }

    /**
     * @MethodName grantPermissions
     * @Param request
     * @Description 授予权限
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantPermissions(GrantPermissionRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        List<String> uids = normalizeUids(request.getUids());
        if (uids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "uids 不能为空");
        }

        long targetRoleId = resolveManageableRoleId(request.getRole());

        // 检查用户是否存在
        ensureUsersExist(uids);

        // 查询已存在的关联，避免重复插入触发唯一约束异常
        List<UserRole> existed = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>()
                        .in(UserRole::getUserUid, uids)
                        .eq(UserRole::getRoleId, targetRoleId)
        );
        Set<String> existedUidSet = existed == null ? Set.of() :
                existed.stream().map(UserRole::getUserUid).collect(Collectors.toSet());

        for (String uid : uids) {
            if (existedUidSet.contains(uid)) {
                continue;
            }
            UserRole ur = new UserRole();
            ur.setUserUid(uid);
            ur.setRoleId(targetRoleId);
            try {
                userRoleMapper.insert(ur);
            } catch (DuplicateKeyException e) {
                // 并发幂等
                log.warn("Grant role duplicated, uid: {}, roleId: {}", uid, targetRoleId);
            }
        }

        uids.forEach(this::refreshAuthCacheSafely);
    }

    /**
     * @MethodName updateUserPermission
     * @Param request
     * @Description 更新用户权限
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserPermission(UpdateUserPermissionRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        String uid = StrUtil.trim(request.getUid());
        if (StrUtil.isBlank(uid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }
        long targetRoleId = resolveManageableRoleId(request.getRole());

        ensureUsersExist(List.of(uid));
        ensureNotRoot(uid);

        // 先清空可管理的角色，再写入新角色
        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserUid, uid)
                        .in(UserRole::getRoleId, userManageProperties.getManageableRoleIds())
        );

        UserRole newRole = new UserRole();
        newRole.setUserUid(uid);
        newRole.setRoleId(targetRoleId);
        userRoleMapper.insert(newRole);

        ensureStudentRole(uid);
        refreshAuthCacheSafely(uid);
    }

    /**
     * @MethodName revokePermission
     * @Param uid
     * @Description 撤销权限
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokePermission(String uid) {
        uid = StrUtil.trim(uid);
        if (StrUtil.isBlank(uid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }

        ensureUsersExist(List.of(uid));
        ensureNotRoot(uid);

        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserUid, uid)
                        .in(UserRole::getRoleId, userManageProperties.getManageableRoleIds())
        );

        ensureStudentRole(uid);
        refreshAuthCacheSafely(uid);
    }

    /**
     * @MethodName batchRevokePermissions
     * @Param request
     * @Description 批量撤销权限
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRevokePermissions(BatchUidsRequest request) {
        List<String> uids = normalizeUids(request);
        if (uids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "uids 不能为空");
        }

        ensureUsersExist(uids);

        for (String uid : uids) {
            ensureNotRoot(uid);
        }

        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>()
                        .in(UserRole::getUserUid, uids)
                        .in(UserRole::getRoleId, userManageProperties.getManageableRoleIds())
        );

        uids.forEach(this::ensureStudentRole);
        uids.forEach(this::refreshAuthCacheSafely);
    }

    /**
     * @MethodName validatePasswordLength
     * @Param password
     * @Description 验证密码长度
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private void validatePasswordLength(@NonNull String password) {
        int minLength = userManageProperties.getPasswordMinLength();
        int maxLength = userManageProperties.getPasswordMaxLength();
        if (password.length() < minLength || password.length() > maxLength) {
            throw new BizException(ResultCode.BAD_REQUEST, "密码长度应在" + minLength + "-" + maxLength + "之间");
        }
    }

    /**
     * @MethodName validateUsernameLength
     * @Param username
     * @Description 验证用户名长度
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private void validateUsernameLength(String username) {
        if (username == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "username 不能为空");
        }
        String name = username.trim();
        int minLength = userManageProperties.getUsernameMinLength();
        int maxLength = userManageProperties.getUsernameMaxLength();
        if (name.length() < minLength || name.length() > maxLength) {
            throw new BizException(ResultCode.INVALID_USERNAME, "用户名长度应在" + minLength + "-" + maxLength + "之间");
        }
    }

    /**
     * @MethodName resolveCollegeId
     * @Param collegeId
     * @Param collegeId
     * @Description 解析学院id
     * @Return @return {@link Long }
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Nullable
    private Long resolveCollegeId(Long collegeId) {
        if (collegeId != null) {
            SysCollege college = sysCollegeMapper.selectById(collegeId);
            if (college == null) {
                throw new BizException(ResultCode.COLLEGE_NOT_FOUND, "学院不存在");
            }
            return collegeId;
        }
        return null;
    }

    /**
     * @MethodName resolveClass
     * @Param classId
     * @Param classId
     * @Param classId
     * @Description 解析班级id
     * @Return @return {@link SysClass }
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    @Nullable
    private SysClass resolveClassId(Long classId, Long collegeId) {
        if (classId != null) {
            SysClass sysClass = sysClassMapper.selectById(classId);
            if (sysClass == null) {
                throw new BizException(ResultCode.CLASS_NOT_FOUND, "班级不存在");
            }
            if (collegeId != null && sysClass.getCollegeId() != null && !collegeId.equals(sysClass.getCollegeId())) {
                throw new BizException(ResultCode.BAD_REQUEST, "班级与学院不匹配");
            }

            return sysClass;
        }
        return null;
    }

    /**
     * @MethodName resolveStatus
     * @Param status
     * @Description 解析状态
     * @Return @return {@link Integer }
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private Integer resolveStatus(Integer status) {
        if (status == null) {
            return null;
        }
        if (UserStatusConstant.NORMAL == status || UserStatusConstant.DISABLED == status) {
            return status;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "status 参数不合法");
    }

    /**
     * @MethodName normalizeUids
     * @Param request
     * @Description 标准化uid
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private List<String> normalizeUids(BatchUidsRequest request) {
        if (request == null || request.getUids() == null) {
            return List.of();
        }
        return normalizeUids(request.getUids());
    }

    /**
     * @MethodName normalizeUids
     * @Param uids
     * @Description 标准化uid
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private List<String> normalizeUids(List<String> uids) {
        if (uids == null) {
            return List.of();
        }
        return uids.stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
    }

    /**
     * @MethodName ensureUsersExist
     * @Param uids
     * @Description 确保用户存在
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private void ensureUsersExist(List<String> uids) {
        if (uids == null || uids.isEmpty()) {
            return;
        }
        List<UserInfo> users = userInfoMapper.selectList(new LambdaQueryWrapper<UserInfo>().in(UserInfo::getUid, uids));
        Set<String> existed = users == null ? Set.of() : users.stream().map(UserInfo::getUid).collect(Collectors.toSet());
        for (String uid : uids) {
            if (!existed.contains(uid)) {
                throw new BizException(ResultCode.USER_NOT_FOUND, "用户不存在: " + uid);
            }
        }
    }

    /**
     * @MethodName ensureNotRoot
     * @Param uid
     * @Description 确保不是root
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private void ensureNotRoot(String uid) {
        List<UserRole> roles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserUid, uid)
                        .eq(UserRole::getRoleId, RoleIdConstant.ROOT)
        );
        if (roles != null && !roles.isEmpty()) {
            throw new BizException(ResultCode.FORBIDDEN, "禁止修改 root 用户权限");
        }
    }

    /**
     * @MethodName ensureStudentRole
     * @Param uid
     * @Description 确保学生角色
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private void ensureStudentRole(String uid) {
        Long count = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserUid, uid)
                        .eq(UserRole::getRoleId, RoleIdConstant.STUDENT)
        );
        if (count != null && count > 0) {
            return;
        }

        UserRole ur = new UserRole();
        ur.setUserUid(uid);
        ur.setRoleId(RoleIdConstant.STUDENT);
        try {
            userRoleMapper.insert(ur);
        } catch (DuplicateKeyException e) {
            // 幂等
        }
    }

    /**
     * @MethodName resolveManageableRoleId
     * @Param role
     * @Description 解析可管理角色id
     * @Return @return long
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private long resolveManageableRoleId(String role) {
        if (role == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "role 不能为空");
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "role 不能为空");
        }
        return switch (normalized) {
            case RoleConstant.ADMIN -> RoleIdConstant.ADMIN;
            case RoleConstant.TEACHER -> RoleIdConstant.TEACHER;
            case RoleConstant.TA -> RoleIdConstant.TA;
            default -> throw new BizException(ResultCode.BAD_REQUEST, "role 参数不合法");
        };
    }

    /**
     * @MethodName generatePassword
     * <p>
     * @Description 生成密码
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private @NonNull String generatePassword() {
//        int minLen = userManageProperties.getPasswordMinLength();
//        int maxLen = userManageProperties.getPasswordMaxLength();
//        int len = userManageProperties.getGeneratedPasswordLength();
//        if (len < minLen) {
//            len = minLen;
//        }
//        if (len > maxLen) {
//            len = maxLen;
//        }
//
//        String chars = userManageProperties.getPasswordChars();
//        if (StrUtil.isBlank(chars)) {
//            chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*";
//        }
//
//        char[] charArray = chars.toCharArray();
//        StringBuilder sb = new StringBuilder(len);
//        for (int i = 0; i < len; i++) {
//            sb.append(charArray[secureRandom.nextInt(charArray.length)]);
//        }
//        return sb.toString();
        //TODO: 提供固定密码和随机密码两种方法
        return "$2a$10$IgytfIALeNqnwQCXnAdOAe4AfGEHqZOzCaLu6MDwMH78Bum4ZQSua";
    }

    /**
     * @MethodName generateUuid32
     * <p>
     * @Description 生成uuid32
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private @NonNull String generateUuid32() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * @MethodName kickoutUserSafely
     * @Param uid
     * @Description 安全驱逐用户
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private void kickoutUserSafely(String uid) {
        try {
            // 先记录 token 列表，再逐个 logout，确保清理 token/session 等 Redis 数据
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
            // 清理鉴权缓存
            deleteUserAuthCache(uid);
        }
    }

    /**
     * @MethodName deleteUserAuthCache
     * @Param uid
     * @Description 删除用户身份验证缓存
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
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
     * @MethodName refreshAuthCacheSafely
     * @Param uid
     * @Description 安全刷新身份验证缓存
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private void refreshAuthCacheSafely(String uid) {
        try {
            Result<Void> result = authInternalFeignClient.refreshUserAuthCache(uid);
            if (result == null || result.getCode() != ResultCode.SUCCESS) {
                log.warn("Refresh auth cache failed, uid: {}, result: {}", uid, result);
            }
        } catch (Exception e) {
            log.warn("Refresh auth cache failed, uid: {}", uid, e);
        }
    }

    /**
     * @MethodName fillRolesForUsers
     * @Param records
     * @Param uidExtractor
     * @Param roleSetter
     * @Description 为用户填充角色
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private <T> void fillRolesForUsers(List<T> records,
                                       java.util.function.Function<T, String> uidExtractor,
                                       java.util.function.BiConsumer<T, List<String>> roleSetter) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<String> uids = records.stream()
                .map(uidExtractor)
                .filter(StrUtil::isNotBlank)
                .toList();
        Map<String, List<String>> rolesMap = queryUserRolesMap(uids);
        for (T record : records) {
            String uid = uidExtractor.apply(record);
            roleSetter.accept(record, rolesMap.getOrDefault(uid, Collections.emptyList()));
        }
    }

    /**
     * @MethodName fillRolesForPermissionUsers
     * @Param records
     * @Description 为权限用户填充角色
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private void fillRolesForPermissionUsers(List<PermissionUserVo> records) {
        fillRolesForUsers(records, PermissionUserVo::getUid, PermissionUserVo::setRoles);
    }

    /**
     * @MethodName fillRolesForSearchedUsers
     * @Param records
     * @Description 为搜索到用户填充角色
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private void fillRolesForSearchedUsers(List<UserSearchVo> records) {
        fillRolesForUsers(records, UserSearchVo::getUid, UserSearchVo::setRoles);
    }



    /**
     * @MethodName fillRolesForUserList
     * @Param records
     * @Description 为用户列表填充角色
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private void fillRolesForUserList(List<UserListVo> records) {
        fillRolesForUsers(records, UserListVo::getUid, UserListVo::setRoles);
    }

    /**
     * @MethodName queryUserRolesMap
     * @Param uids
     * @Description 查询用户角色映射
     * @Return @return {@link Map }<{@link String }, {@link List }<{@link String }>>
     * @Author HaoRan_Lyu
     * @Date 2026/02/15
     */
    private Map<String, List<String>> queryUserRolesMap(List<String> uids) {
        if (uids == null || uids.isEmpty()) {
            return Map.of();
        }
        List<UserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().in(UserRole::getUserUid, uids));
        if (userRoles == null || userRoles.isEmpty()) {
            return Map.of();
        }
        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        if (roles == null || roles.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> roleIdToCode = roles.stream()
                .filter(r -> r.getId() != null && StrUtil.isNotBlank(r.getRole()))
                .collect(Collectors.toMap(Role::getId, Role::getRole, (a, b) -> a));

        // roleId 升序输出，保持稳定顺序
        return userRoles.stream()
                .filter(ur -> StrUtil.isNotBlank(ur.getUserUid()) && ur.getRoleId() != null)
                .sorted(Comparator.comparingLong(UserRole::getRoleId))
                .collect(Collectors.groupingBy(
                        UserRole::getUserUid,
                        Collectors.mapping(ur -> roleIdToCode.getOrDefault(ur.getRoleId(), ""), Collectors.toList())
                ))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().filter(StrUtil::isNotBlank).distinct().toList()
                ));
    }
}
