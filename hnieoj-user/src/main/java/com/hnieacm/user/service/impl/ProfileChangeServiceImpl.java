package com.hnieacm.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.constant.AuthCacheConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.constant.ProfileChangeStatusConstant;
import com.hnieacm.user.dto.ProfileChangeCreateRequest;
import com.hnieacm.user.entity.SysClass;
import com.hnieacm.user.entity.SysCollege;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.entity.UserProfileChange;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.SysCollegeMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserProfileChangeMapper;
import com.hnieacm.user.service.ProfileChangeService;
import com.hnieacm.user.vo.ProfileChangeVo;
import com.hnieacm.user.vo.ProfileIdentityVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 用户身份资料变更申请服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileChangeServiceImpl implements ProfileChangeService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final int MAX_REASON_LENGTH = 1000;

    private final UserProfileChangeMapper userProfileChangeMapper;
    private final UserInfoMapper userInfoMapper;
    private final SysCollegeMapper sysCollegeMapper;
    private final SysClassMapper sysClassMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createChangeRequest(String uid, ProfileChangeCreateRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        // 用户行锁：同一用户并发提交串行化，保证仅一条待审申请。
        UserInfo user = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUid, uid).last("FOR UPDATE")
        );
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }

        Long pendingCount = userProfileChangeMapper.selectCount(
                new LambdaQueryWrapper<UserProfileChange>()
                        .eq(UserProfileChange::getUid, uid)
                        .eq(UserProfileChange::getStatus, ProfileChangeStatusConstant.PENDING)
        );
        if (pendingCount != null && pendingCount > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "已存在待审核的变更申请");
        }

        String realname = validateIdentity(request.getRealname(), request.getCollegeId(),
                request.getGrade(), request.getClassId());
        String reason = StrUtil.trim(request.getReason());
        if (StrUtil.isBlank(reason)) {
            throw new BizException(ResultCode.BAD_REQUEST, "reason 不能为空");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "reason 长度不能超过 1000");
        }

        ProfileIdentityVo original = new ProfileIdentityVo(
                user.getRealname(), user.getCollegeId(), user.getGrade(), user.getClassId());
        ProfileIdentityVo proposed = new ProfileIdentityVo(
                realname, request.getCollegeId(), request.getGrade().trim(), request.getClassId());

        UserProfileChange change = new UserProfileChange();
        change.setUid(uid);
        change.setOriginal(writeJson(original));
        change.setProposed(writeJson(proposed));
        change.setReason(reason);
        change.setStatus(ProfileChangeStatusConstant.PENDING);
        userProfileChangeMapper.insert(change);
        log.info("Profile change request created, id: {}, uid: {}", change.getId(), uid);
    }

    @Override
    public PageVo<ProfileChangeVo> listMyChangeRequests(String uid, int page, int pageSize) {
        validatePage(page, pageSize);
        LambdaQueryWrapper<UserProfileChange> wrapper = new LambdaQueryWrapper<UserProfileChange>()
                .eq(UserProfileChange::getUid, uid)
                .orderByDesc(UserProfileChange::getGmtCreate, UserProfileChange::getId);
        return queryPage(page, pageSize, wrapper);
    }

    @Override
    public PageVo<ProfileChangeVo> listAdminChangeRequests(int page, int pageSize, String status, String keyword) {
        validatePage(page, pageSize);

        String normalizedStatus = null;
        if (status != null && !status.trim().isEmpty()) {
            normalizedStatus = ProfileChangeStatusConstant.normalize(status);
            if (normalizedStatus == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "status 只能为 PENDING/APPROVED/REJECTED");
            }
        }

        LambdaQueryWrapper<UserProfileChange> wrapper = new LambdaQueryWrapper<>();
        if (normalizedStatus != null) {
            wrapper.eq(UserProfileChange::getStatus, normalizedStatus);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(UserProfileChange::getUid, kw)
                    .or()
                    .like(UserProfileChange::getReason, kw));
        }
        wrapper.orderByDesc(UserProfileChange::getGmtCreate, UserProfileChange::getId);
        return queryPage(page, pageSize, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String reason, String reviewerUid) {
        // 统一锁序：先锁申请行，再锁用户行，避免与其它审核事务死锁。
        UserProfileChange change = requireLockedChange(id);
        if (ProfileChangeStatusConstant.APPROVED.equals(change.getStatus())) {
            return;
        }
        if (ProfileChangeStatusConstant.REJECTED.equals(change.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "申请已驳回，不能再次通过");
        }

        UserInfo user = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUid, change.getUid()).last("FOR UPDATE")
        );
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }

        ProfileIdentityVo original = readJson(change.getOriginal());
        if (!identityMatchesUser(user, original)) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户资料已发生变化，申请已失效");
        }

        ProfileIdentityVo proposed = readJson(change.getProposed());
        String realname = validateIdentity(proposed.getRealname(), proposed.getCollegeId(),
                proposed.getGrade(), proposed.getClassId());

        user.setRealname(realname);
        user.setCollegeId(proposed.getCollegeId());
        user.setGrade(proposed.getGrade().trim());
        user.setClassId(proposed.getClassId());
        userInfoMapper.updateById(user);

        LocalDateTime now = LocalDateTime.now();
        change.setStatus(ProfileChangeStatusConstant.APPROVED);
        change.setReviewerUid(reviewerUid);
        change.setReviewReason(normalizeReviewReason(reason));
        change.setReviewAt(now);
        userProfileChangeMapper.updateById(change);

        // 身份信息变更：提交后清理鉴权缓存（不改动任何角色/权限行）。
        runAfterCommit(() -> clearUserAuthCache(change.getUid()));
        log.info("Profile change approved, id: {}, uid: {}, reviewer: {}", id, change.getUid(), reviewerUid);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason, String reviewerUid) {
        UserProfileChange change = requireLockedChange(id);
        if (ProfileChangeStatusConstant.REJECTED.equals(change.getStatus())) {
            return;
        }
        if (ProfileChangeStatusConstant.APPROVED.equals(change.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "申请已通过，不能再次驳回");
        }

        String normalizedReason = normalizeReviewReason(reason);
        if (normalizedReason == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "驳回原因不能为空");
        }

        change.setStatus(ProfileChangeStatusConstant.REJECTED);
        change.setReviewerUid(reviewerUid);
        change.setReviewReason(normalizedReason);
        change.setReviewAt(LocalDateTime.now());
        userProfileChangeMapper.updateById(change);
        log.info("Profile change rejected, id: {}, uid: {}, reviewer: {}", id, change.getUid(), reviewerUid);
    }

    private UserProfileChange requireLockedChange(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 不能为空");
        }
        UserProfileChange change = userProfileChangeMapper.selectOne(
                new LambdaQueryWrapper<UserProfileChange>().eq(UserProfileChange::getId, id).last("FOR UPDATE")
        );
        if (change == null) {
            throw new BizException(ResultCode.NOT_FOUND, "变更申请不存在");
        }
        return change;
    }

    /**
     * 校验实名/学院/年级/班级归属，返回 trim 后的实名。
     */
    private String validateIdentity(String realname, Long collegeId, String grade, Long classId) {
        String trimmedRealname = StrUtil.trim(realname);
        if (StrUtil.isBlank(trimmedRealname)) {
            throw new BizException(ResultCode.BAD_REQUEST, "realname 不能为空");
        }
        if (trimmedRealname.length() > 50) {
            throw new BizException(ResultCode.BAD_REQUEST, "realname 长度不能超过 50");
        }
        String trimmedGrade = StrUtil.trim(grade);
        if (StrUtil.isBlank(trimmedGrade)) {
            throw new BizException(ResultCode.BAD_REQUEST, "grade 不能为空");
        }
        if (trimmedGrade.length() > 20) {
            throw new BizException(ResultCode.BAD_REQUEST, "grade 长度不能超过 20");
        }
        if (collegeId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "collegeId 不能为空");
        }
        if (classId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "classId 不能为空");
        }

        SysCollege college = sysCollegeMapper.selectById(collegeId);
        if (college == null) {
            throw new BizException(ResultCode.COLLEGE_NOT_FOUND, "学院不存在");
        }
        SysClass sysClass = sysClassMapper.selectById(classId);
        if (sysClass == null) {
            throw new BizException(ResultCode.CLASS_NOT_FOUND, "班级不存在");
        }
        if (sysClass.getCollegeId() != null && !sysClass.getCollegeId().equals(collegeId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "班级与学院不匹配");
        }
        if (StrUtil.isNotBlank(sysClass.getGrade()) && !sysClass.getGrade().equals(trimmedGrade)) {
            throw new BizException(ResultCode.BAD_REQUEST, "班级与年级不匹配");
        }
        Long gradeCount = sysClassMapper.selectCount(
                new LambdaQueryWrapper<SysClass>()
                        .eq(SysClass::getCollegeId, collegeId)
                        .eq(SysClass::getGrade, trimmedGrade)
        );
        if (gradeCount == null || gradeCount == 0) {
            throw new BizException(ResultCode.GRADE_NOT_FOUND, "该学院下不存在该年级");
        }
        return trimmedRealname;
    }

    private boolean identityMatchesUser(UserInfo user, ProfileIdentityVo original) {
        if (original == null) {
            return false;
        }
        return Objects.equals(user.getRealname(), original.getRealname())
                && Objects.equals(user.getCollegeId(), original.getCollegeId())
                && Objects.equals(user.getGrade(), original.getGrade())
                && Objects.equals(user.getClassId(), original.getClassId());
    }

    private String normalizeReviewReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "reason 长度不能超过 1000");
        }
        return trimmed;
    }

    private void validatePage(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 和 pageSize 必须大于 0");
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ResultCode.BAD_REQUEST, "pageSize 不能超过 100");
        }
    }

    private PageVo<ProfileChangeVo> queryPage(int page, int pageSize,
                                              LambdaQueryWrapper<UserProfileChange> wrapper) {
        Page<UserProfileChange> mpPage = new Page<>(page, pageSize);
        Page<UserProfileChange> result = userProfileChangeMapper.selectPage(mpPage, wrapper);
        List<ProfileChangeVo> list = result.getRecords().stream().map(this::toVo).toList();
        return new PageVo<>(list, result.getTotal());
    }

    private void clearUserAuthCache(String uid) {
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

    private String writeJson(ProfileIdentityVo identity) {
        try {
            return objectMapper.writeValueAsString(identity);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "身份资料序列化失败");
        }
    }

    private ProfileIdentityVo readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ProfileIdentityVo.class);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "身份资料解析失败");
        }
    }

    private ProfileChangeVo toVo(UserProfileChange change) {
        ProfileChangeVo vo = new ProfileChangeVo();
        vo.setId(change.getId());
        vo.setUid(change.getUid());
        vo.setOriginal(readJson(change.getOriginal()));
        vo.setProposed(readJson(change.getProposed()));
        vo.setReason(change.getReason());
        vo.setStatus(change.getStatus());
        vo.setReviewerUid(change.getReviewerUid());
        vo.setReviewReason(change.getReviewReason());
        vo.setReviewAt(change.getReviewAt());
        vo.setGmtCreate(change.getGmtCreate());
        vo.setGmtModified(change.getGmtModified());
        return vo;
    }
}
