package com.hnieacm.achievement.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.achievement.constant.AchievementApplyStatus;
import com.hnieacm.achievement.constant.UserAchievementStatus;
import com.hnieacm.achievement.entity.AchievementApply;
import com.hnieacm.achievement.entity.UserAchievement;
import com.hnieacm.achievement.entity.UserInfo;
import com.hnieacm.achievement.mapper.AchievementApplyMapper;
import com.hnieacm.achievement.mapper.UserAchievementMapper;
import com.hnieacm.achievement.mapper.UserInfoMapper;
import com.hnieacm.achievement.service.AchievementApplyService;
import com.hnieacm.achievement.service.AchievementFileService;
import com.hnieacm.achievement.vo.AchievementApplyAdminVo;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 成就认证申请服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementApplyServiceImpl implements AchievementApplyService {

    private final AchievementApplyMapper achievementApplyMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final UserInfoMapper userInfoMapper;
    private final AchievementFileService achievementFileService;

    /**
     * @MethodName submitApply
     * @Param loginUid
     * @Param title
     * @Param description
     * @Param file
     * @Description 提交申请
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitApply(String loginUid, String title, String description, MultipartFile file) {
        loginUid = StrUtil.trim(loginUid);
        if (StrUtil.isBlank(loginUid)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录");
        }
        title = StrUtil.trim(title);
        if (StrUtil.isBlank(title)) {
            throw new BizException(ResultCode.BAD_REQUEST, "title 不能为空");
        }

        String fileUrl = achievementFileService.store(loginUid, file);

        AchievementApply apply = new AchievementApply();
        apply.setUid(loginUid);
        apply.setTitle(title);
        apply.setDescription(StrUtil.trimToNull(description));
        apply.setFileUrl(fileUrl);
        apply.setStatus(AchievementApplyStatus.PENDING);
        apply.setReason(null);
        apply.setGmtCreate(LocalDateTime.now());
        apply.setGmtModified(LocalDateTime.now());

        achievementApplyMapper.insert(apply);
    }

    /**
     * @MethodName listForAdmin
     * @Param page
     * @Param pageSize
     * @Param keyword
     * @Param status
     * @Param collegeId
     * @Description 管理员查询申请列表
     * @Return @return {@link PageVo }<{@link AchievementApplyAdminVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/18
     */
    @Override
    public PageVo<AchievementApplyAdminVo> listForAdmin(int page, int pageSize, String keyword, String status, Long collegeId) {
        validateAdminListParams(page, pageSize, collegeId);

        String normalizedKeyword = StrUtil.trimToNull(keyword);
        String normalizedStatus = normalizeStatus(status);

        Page<AchievementApplyAdminVo> mpPage = new Page<>(page, pageSize);
        var result = achievementApplyMapper.selectAdminApplyPage(mpPage, normalizedKeyword, normalizedStatus, collegeId);
        if (result.getRecords().isEmpty()) {
            return new PageVo<>(Collections.emptyList(), result.getTotal());
        }
        return new PageVo<>(result.getRecords(), result.getTotal());
    }

    /**
     * @MethodName approve
     * @Param id
     * @Description 批准
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 不合法");
        }

        AchievementApply apply = achievementApplyMapper.selectById(id);
        if (apply == null) {
            throw new BizException(ResultCode.NOT_FOUND, "申请不存在");
        }
        if (AchievementApplyStatus.APPROVED.equalsIgnoreCase(apply.getStatus())) {
            // 幂等：已通过直接返回
            return;
        }
        if (AchievementApplyStatus.REJECTED.equalsIgnoreCase(apply.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "申请已驳回，无法通过");
        }

        long existed = userInfoMapper.selectCount(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUid, apply.getUid()));
        if (existed == 0) {
            throw new BizException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }

        UserAchievement achievement = new UserAchievement();
        achievement.setUid(apply.getUid());
        achievement.setTitle(apply.getTitle());
        achievement.setContent(apply.getDescription());
        achievement.setProofUrl(apply.getFileUrl());
        achievement.setAchieveTime(LocalDateTime.now());
        achievement.setStatus(UserAchievementStatus.APPROVED);
        userAchievementMapper.insert(achievement);

        apply.setStatus(AchievementApplyStatus.APPROVED);
        apply.setReason(null);
        apply.setGmtModified(LocalDateTime.now());
        achievementApplyMapper.updateById(apply);

        log.info("Achievement apply approved, id: {}, uid: {}", id, apply.getUid());
    }

    /**
     * @MethodName reject
     * @Param id
     * @Param reason
     * @Description 拒绝
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 不合法");
        }
        reason = StrUtil.trim(reason);
        if (StrUtil.isBlank(reason)) {
            throw new BizException(ResultCode.BAD_REQUEST, "reason 不能为空");
        }

        AchievementApply apply = achievementApplyMapper.selectById(id);
        if (apply == null) {
            throw new BizException(ResultCode.NOT_FOUND, "申请不存在");
        }
        if (AchievementApplyStatus.APPROVED.equalsIgnoreCase(apply.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "申请已通过，无法驳回");
        }

        apply.setStatus(AchievementApplyStatus.REJECTED);
        apply.setReason(reason);
        apply.setGmtModified(LocalDateTime.now());
        achievementApplyMapper.updateById(apply);

        log.info("Achievement apply rejected, id: {}, uid: {}", id, apply.getUid());
    }

    /**
     * @MethodName normalizeStatus
     * @Param status
     * @Description 标准化状态
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/18
     */
    private String normalizeStatus(String status) {
        String normalizedStatus = StrUtil.trimToNull(status);
        if (normalizedStatus == null) {
            return null;
        }
        if (AchievementApplyStatus.PENDING.equalsIgnoreCase(normalizedStatus)) {
            return AchievementApplyStatus.PENDING;
        }
        if (AchievementApplyStatus.APPROVED.equalsIgnoreCase(normalizedStatus)) {
            return AchievementApplyStatus.APPROVED;
        }
        if (AchievementApplyStatus.REJECTED.equalsIgnoreCase(normalizedStatus)) {
            return AchievementApplyStatus.REJECTED;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "status 仅支持 pending/approved/rejected");
    }

    /**
     * @MethodName validateAdminListParams
     * @Param page
     * @Param pageSize
     * @Param collegeId
     * @Description 验证管理员查询申请列表参数
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/18
     */
    private void validateAdminListParams(int page, int pageSize, Long collegeId) {
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 和 pageSize 必须大于 0");
        }
        if (collegeId != null && collegeId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "collegeId 不合法");
        }
    }
}
