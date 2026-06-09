package com.hnieacm.user.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.constant.RoleIdConstant;
import com.hnieacm.common.constant.UserStatusConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.common.util.PageParamUtils;
import com.hnieacm.user.dto.BatchUidsRequest;
import com.hnieacm.user.dto.CreateUserRequest;
import com.hnieacm.user.dto.GrantPermissionRequest;
import com.hnieacm.user.dto.UpdateUserPasswordRequest;
import com.hnieacm.user.dto.UpdateUserPermissionRequest;
import com.hnieacm.user.dto.UpdateUserRequest;
import com.hnieacm.user.dto.UserContextDto;
import com.hnieacm.user.entity.SysClass;
import com.hnieacm.user.entity.SysCollege;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.entity.UserRole;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.SysCollegeMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserRoleMapper;
import com.hnieacm.user.properties.UserManageProperties;
import com.hnieacm.user.service.UserManageService;
import com.hnieacm.user.service.manager.UserInfoManager;
import com.hnieacm.user.service.support.UserAuthStateService;
import com.hnieacm.user.service.support.UserManageValidator;
import com.hnieacm.user.service.support.UserRoleViewAssembler;
import com.hnieacm.user.vo.CreateUserVo;
import com.hnieacm.user.vo.PermissionUserVo;
import com.hnieacm.user.vo.UserDetailVo;
import com.hnieacm.user.vo.UserListVo;
import com.hnieacm.user.vo.UserSearchVo;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserManageProperties userManageProperties;
    private final UserInfoManager userInfoManager;
    private final UserManageValidator userManageValidator;
    private final UserAuthStateService userAuthStateService;
    private final UserRoleViewAssembler userRoleViewAssembler;

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

        userManageValidator.validateUsernameLength(request.getUsername());

        Long collegeId = resolveCollegeId(request.getCollegeId());
        SysClass sysClass = resolveClassId(request.getClassId(), collegeId);

        String grade = request.getGrade();
        if (StrUtil.isBlank(grade) && sysClass != null) {
            grade = sysClass.getGrade();
        }

        String plainPassword = request.getPassword();
        String initialPassword = null;
        boolean passwordResetRequired = false;
        if (StrUtil.isBlank(plainPassword)) {
            initialPassword = userManageValidator.generateDefaultPassword();
            plainPassword = initialPassword;
            passwordResetRequired = true;
        }
        userManageValidator.validatePasswordLength(plainPassword);

        UserInfo user = new UserInfo();
        user.setUuid(generateUuid32());
        user.setUid(uid);
        user.setUsername(request.getUsername());
        user.setPassword(BCrypt.hashpw(plainPassword));
        user.setPasswordResetRequired(passwordResetRequired);
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
            userManageValidator.validateUsernameLength(request.getUsername());
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

        Integer status = userManageValidator.resolveStatus(request.getStatus());
        boolean needKickout = false;
        if (status != null) {
            user.setStatus(status);
            needKickout = status == UserStatusConstant.DISABLED;
        }

        userInfoMapper.updateById(user);

        if (needKickout) {
            userAuthStateService.kickoutUserSafely(uid);
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
        userManageValidator.validatePasswordLength(request.getPassword());

        UserInfo user = userInfoManager.getUserByUid(uid);

        user.setPassword(BCrypt.hashpw(request.getPassword()));
        user.setPasswordResetRequired(false);
        userInfoMapper.updateById(user);

        // 重置密码后强制下线
        userAuthStateService.kickoutUserSafely(uid);
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
        userAuthStateService.kickoutUserSafely(uid);

        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserUid, uid));
        userInfoMapper.deleteById(user.getUuid());

        // 删除用户后角色/权限缓存无意义：直接清理缓存
        userAuthStateService.deleteUserAuthCache(uid);
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
        List<String> uids = userManageValidator.normalizeUids(request);
        if (uids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "uids 不能为空");
        }

        userInfoMapper.update(
                null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserInfo>()
                        .in(UserInfo::getUid, uids)
                        .set(UserInfo::getStatus, UserStatusConstant.DISABLED)
        );

        uids.forEach(userAuthStateService::kickoutUserSafely);
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
        List<String> uids = userManageValidator.normalizeUids(request);
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
        List<String> uids = userManageValidator.normalizeUids(request);
        if (uids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "uids 不能为空");
        }

        // 先下线
        uids.forEach(userAuthStateService::kickoutUserSafely);

        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().in(UserRole::getUserUid, uids));
        userInfoMapper.delete(new LambdaQueryWrapper<UserInfo>().in(UserInfo::getUid, uids));

        uids.forEach(userAuthStateService::deleteUserAuthCache);
    }


    @Override
    public PageVo<UserListVo> listUsers(String keyword, Long collegeId, String grade, Long classId, int page, int pageSize) {
        PageParamUtils.validate(page, pageSize);

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
        userRoleViewAssembler.fillRolesForUserList(result.getRecords());
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

        List<String> roles = userRoleViewAssembler.getRoles(uid);
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
        PageParamUtils.validate(page, pageSize);
        if (userManageProperties.getManageableRoleIds() == null || userManageProperties.getManageableRoleIds().isEmpty()) {
            return new PageVo<>(Collections.emptyList(), 0);
        }

        Page<PermissionUserVo> mpPage = new Page<>(page, pageSize);
        Page<PermissionUserVo> result =
                (Page<PermissionUserVo>) userInfoMapper.selectPermissionUsers(mpPage, userManageProperties.getManageableRoleIds());
        userRoleViewAssembler.fillRolesForPermissionUsers(result.getRecords());
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
        PageParamUtils.validate(page, pageSize);
        if (StrUtil.isBlank(query)) {
            throw new BizException(ResultCode.BAD_REQUEST, "query 不能为空");
        }
        Page<UserSearchVo> mpPage = new Page<>(page, pageSize);
        Page<UserSearchVo> result = (Page<UserSearchVo>) userInfoMapper.searchUsers(mpPage, query.trim());
        userRoleViewAssembler.fillRolesForSearchedUsers(result.getRecords());
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
        List<String> uids = userManageValidator.normalizeUids(request.getUids());
        if (uids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "uids 不能为空");
        }

        long targetRoleId = userManageValidator.resolveManageableRoleId(request.getRole());

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

        uids.forEach(userAuthStateService::refreshAuthCacheSafely);
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
        long targetRoleId = userManageValidator.resolveManageableRoleId(request.getRole());

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
        userAuthStateService.refreshAuthCacheSafely(uid);
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
        userAuthStateService.refreshAuthCacheSafely(uid);
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
        List<String> uids = userManageValidator.normalizeUids(request);
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
        uids.forEach(userAuthStateService::refreshAuthCacheSafely);
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

}
