package com.hnieacm.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.auth.dto.LoginRequest;
import com.hnieacm.auth.dto.LoginVo;
import com.hnieacm.auth.dto.RegisterRequest;
import com.hnieacm.auth.entity.SysClass;
import com.hnieacm.auth.entity.SysCollege;
import com.hnieacm.auth.entity.UserInfo;
import com.hnieacm.auth.entity.UserRegisterApply;
import com.hnieacm.auth.mapper.SysClassMapper;
import com.hnieacm.auth.mapper.SysCollegeMapper;
import com.hnieacm.auth.mapper.UserInfoMapper;
import com.hnieacm.auth.mapper.UserRegisterApplyMapper;
import com.hnieacm.auth.properties.AuthValidationProperties;
import com.hnieacm.auth.service.AuthPermissionService;
import com.hnieacm.auth.service.UserAuthCacheService;
import com.hnieacm.auth.service.AuthService;
import com.hnieacm.common.constant.RegisterStatus;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.constant.UserStatusConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: 认证服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserInfoMapper userInfoMapper;
    private final UserRegisterApplyMapper userRegisterApplyMapper;
    private final SysCollegeMapper sysCollegeMapper;
    private final SysClassMapper sysClassMapper;
    private final AuthPermissionService authPermissionService;
    private final UserAuthCacheService userAuthCacheService;
    private final AuthValidationProperties authValidationProperties;

    /**
     * @MethodName login
     * @Param request
     * @Description 登录
     * @Return @return {@link LoginVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @Override
    public LoginVo login(LoginRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }

        // 查询用户
        UserInfo user = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUid, request.getUid())
        );

        // 验证用户名和密码
        if (user == null || !BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }

        // 检查用户状态
        if (user.getStatus() == null || user.getStatus() != UserStatusConstant.NORMAL) {
            throw new BizException(ResultCode.USER_DISABLED, "用户已被禁用");
        }

        // 执行登录
        StpUtil.login(user.getUid());
        String token = StpUtil.getTokenValue();

        // 登录成功后写入角色/权限缓存，供网关鉴权读取
        userAuthCacheService.cacheUserAuth(user.getUid());

        List<String> roles = authPermissionService.getUserRoles(user.getUid());
        if (roles == null || roles.isEmpty()) {
            roles = List.of(RoleConstant.STUDENT);
        }

        LoginVo.UserInfoVo userInfo = new LoginVo.UserInfoVo(
                user.getUid(),
                user.getUsername(),
                roles,
                Boolean.TRUE.equals(user.getPasswordResetRequired())
        );
        return new LoginVo(token, userInfo);
    }

    /**
     * @MethodName register
     * @Param request
     * @Description 注册
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }

        // 校验密码长度
        int passwordMinLength = authValidationProperties.getPasswordMinLength();
        int passwordMaxLength = authValidationProperties.getPasswordMaxLength();
        if (request.getPassword().length() < passwordMinLength || request.getPassword().length() > passwordMaxLength) {
            throw new BizException(ResultCode.BAD_REQUEST, "密码长度应在" + passwordMinLength + "-" + passwordMaxLength + "之间");
        }

        // 校验 uid 是否已存在
        Long userCount = userInfoMapper.selectCount(
                new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUid, request.getUid())
        );
        if (userCount > 0) {
            throw new BizException(ResultCode.USER_ALREADY_EXISTS, "该uid已注册");
        }

        Long applyCount = userRegisterApplyMapper.selectCount(
                new LambdaQueryWrapper<UserRegisterApply>().eq(UserRegisterApply::getUid, request.getUid())
        );
        if (applyCount > 0) {
            throw new BizException(ResultCode.USER_ALREADY_EXISTS, "该uid已提交注册申请");
        }

        // 校验用户名长度
        int usernameMinLength = authValidationProperties.getUsernameMinLength();
        int usernameMaxLength = authValidationProperties.getUsernameMaxLength();
        if (request.getUsername().length() < usernameMinLength || request.getUsername().length() > usernameMaxLength) {
            throw new BizException(ResultCode.INVALID_USERNAME, "用户名长度应在" + usernameMinLength + "-" + usernameMaxLength + "之间");
        }

        // 校验邮箱格式
        if (!Validator.isEmail(request.getEmail())) {
            throw new BizException(ResultCode.INVALID_EMAIL, "邮箱格式不正确");
        }

        // 校验学院是否存在
        SysCollege college = sysCollegeMapper.selectById(request.getCollegeId());
        if (college == null) {
            throw new BizException(ResultCode.COLLEGE_NOT_FOUND, "学院不存在");
        }

        // 校验班级是否存在
        SysClass sysClass = sysClassMapper.selectById(request.getClassId());
        if (sysClass == null) {
            throw new BizException(ResultCode.CLASS_NOT_FOUND, "班级不存在");
        }
        if (sysClass.getCollegeId() != null && !sysClass.getCollegeId().equals(request.getCollegeId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "班级与学院不匹配");
        }
        if (StrUtil.isNotBlank(sysClass.getGrade()) && !sysClass.getGrade().equals(request.getGrade())) {
            throw new BizException(ResultCode.BAD_REQUEST, "班级与年级不匹配");
        }

        // 校验年级是否存在（通过班级表统计）
        Long gradeCount = sysClassMapper.selectCount(
                new LambdaQueryWrapper<SysClass>()
                        .eq(SysClass::getCollegeId, request.getCollegeId())
                        .eq(SysClass::getGrade, request.getGrade())
        );
        if (gradeCount == 0) {
            throw new BizException(ResultCode.GRADE_NOT_FOUND, "该学院下不存在该年级");
        }

        // 校验 QQ 格式
        if (!Validator.isMatchRegex("\\d{5,11}", request.getQq())) {
            throw new BizException(ResultCode.INVALID_QQ, "QQ号格式不正确");
        }

        // 加密密码（bcrypt）
        String hashedPassword = BCrypt.hashpw(request.getPassword());

        // 插入注册申请表
        UserRegisterApply apply = new UserRegisterApply();
        apply.setUid(request.getUid());
        apply.setUsername(request.getUsername());
        apply.setPassword(hashedPassword);
        apply.setEmail(request.getEmail());
        apply.setCollegeId(request.getCollegeId());
        apply.setClassId(request.getClassId());
        apply.setGrade(request.getGrade());
        apply.setQq(request.getQq());
        apply.setStatus(RegisterStatus.PENDING);

        try {
            userRegisterApplyMapper.insert(apply);
        } catch (DuplicateKeyException e) {
            // 并发下兜底：依赖数据库唯一约束
            throw new BizException(ResultCode.USER_ALREADY_EXISTS, "该uid已提交注册申请");
        }
    }

}
