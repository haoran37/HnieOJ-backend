package com.hnieacm.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.auth.entity.UserInfo;
import com.hnieacm.auth.entity.UserRegisterApply;
import com.hnieacm.auth.entity.UserRole;
import com.hnieacm.auth.mapper.UserInfoMapper;
import com.hnieacm.auth.mapper.UserRegisterApplyMapper;
import com.hnieacm.auth.mapper.UserRoleMapper;
import com.hnieacm.auth.service.RegistrationReviewService;
import com.hnieacm.auth.service.UserAuthCacheService;
import com.hnieacm.common.constant.RegisterStatus;
import com.hnieacm.common.constant.RoleIdConstant;
import com.hnieacm.common.constant.UserStatusConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 注册审核服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationReviewServiceImpl implements RegistrationReviewService {

    private final UserRegisterApplyMapper userRegisterApplyMapper;
    private final UserInfoMapper userInfoMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserAuthCacheService userAuthCacheService;
    private final PlatformTransactionManager transactionManager;

    /**
     * @MethodName approve
     * @Param uid
     * @Description 批准
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String uid) {
        doApprove(uid);
    }

    /**
     * @MethodName doApprove
     * @Param uid
     * @Description 批准
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    private void doApprove(String uid) {
        uid = StrUtil.trim(uid);
        if (StrUtil.isBlank(uid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }

        UserRegisterApply apply = userRegisterApplyMapper.selectOne(
                new LambdaQueryWrapper<UserRegisterApply>().eq(UserRegisterApply::getUid, uid)
        );
        if (apply == null) {
            throw new BizException(ResultCode.NOT_FOUND, "注册申请不存在");
        }
        if (apply.getStatus() != null && apply.getStatus() == RegisterStatus.APPROVED) {
            // 幂等：已通过直接返回成功
            return;
        }
        if (apply.getStatus() != null && apply.getStatus() == RegisterStatus.REJECTED) {
            throw new BizException(ResultCode.BAD_REQUEST, "该申请已驳回，无法通过");
        }

        long existedUser = userInfoMapper.selectCount(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUid, uid));
        if (existedUser > 0) {
            throw new BizException(ResultCode.USER_ALREADY_EXISTS, "用户已存在，无需重复审核");
        }

        UserInfo userInfo = new UserInfo();
        userInfo.setUuid(generateUuid32());
        userInfo.setUid(uid);
        userInfo.setUsername(apply.getUsername());
        userInfo.setPassword(apply.getPassword());
        userInfo.setEmail(apply.getEmail());
        userInfo.setCollegeId(apply.getCollegeId());
        userInfo.setClassId(apply.getClassId());
        userInfo.setGrade(apply.getGrade());
        userInfo.setQq(apply.getQq());
        userInfo.setStatus(UserStatusConstant.NORMAL);
        userInfo.setGmtCreate(LocalDateTime.now());
        userInfo.setGmtModified(LocalDateTime.now());

        try {
            userInfoMapper.insert(userInfo);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.USER_ALREADY_EXISTS, "用户已存在");
        }

        // 默认授予 student 角色
        UserRole userRole = new UserRole();
        userRole.setUserUid(uid);
        userRole.setRoleId(RoleIdConstant.STUDENT);
        try {
            userRoleMapper.insert(userRole);
        } catch (DuplicateKeyException e) {
            log.debug("Insert student role duplicated, uid: {}", uid);
        }

        // 更新申请状态，保留审核记录
        apply.setStatus(RegisterStatus.APPROVED);
        apply.setReplyInfo(null);
        userRegisterApplyMapper.updateById(apply);

        // 通过后写入权限缓存，供网关鉴权读取
        userAuthCacheService.cacheUserAuth(uid);

        // TODO: 发送邮件通知（smtp 配置通过 Nacos 管理）
        log.info("Register approved, uid: {}, email: {}", uid, apply.getEmail());
    }

    /**
     * @MethodName reject
     * @Param uid
     * @Param uid
     * @Description 拒绝
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String uid, String reason) {
        uid = StrUtil.trim(uid);
        if (StrUtil.isBlank(uid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }
        reason = StrUtil.trim(reason);
        if (StrUtil.isBlank(reason)) {
            throw new BizException(ResultCode.BAD_REQUEST, "reason 不能为空");
        }

        UserRegisterApply apply = userRegisterApplyMapper.selectOne(
                new LambdaQueryWrapper<UserRegisterApply>().eq(UserRegisterApply::getUid, uid)
        );
        if (apply == null) {
            throw new BizException(ResultCode.NOT_FOUND, "注册申请不存在");
        }
        if (apply.getStatus() != null && apply.getStatus() == RegisterStatus.APPROVED) {
            throw new BizException(ResultCode.BAD_REQUEST, "该申请已通过，无法驳回");
        }

        apply.setStatus(RegisterStatus.REJECTED);
        apply.setReplyInfo(reason);
        userRegisterApplyMapper.updateById(apply);

        // TODO: 发送邮件通知（smtp 配置通过 Nacos 管理）
        log.info("Register rejected, uid: {}, email: {}, reason: {}", uid, apply.getEmail(), reason);
    }

    /**
     * @MethodName batchApprove
     * @Param uids
     * @Description 批量批准
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    @Override
    public String batchApprove(List<String> uids) {
        if (uids == null || uids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "uids 不能为空");
        }

        int success = 0;
        int failure = 0;
        for (String uid : uids) {
            try {
                doApproveInNewTransaction(uid);
                success++;
            } catch (BizException e) {
                failure++;
                log.warn("Batch approve registration failed, uid: {}, msg: {}", uid, e.getMsg());
            } catch (Exception e) {
                failure++;
                log.warn("Batch approve registration failed, uid: {}", uid, e);
            }
        }

        return "成功: " + success + ", " + "失败: " + failure;
    }

    /**
     * @MethodName generateUuid32
     *
     * @Description 生成uuid32
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    private String generateUuid32() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * @MethodName doApproveInNewTransaction
     * @Param uid
     * @Description 在新事务中执行批准
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    private void doApproveInNewTransaction(String uid) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.executeWithoutResult(status -> doApprove(uid));
    }
}
