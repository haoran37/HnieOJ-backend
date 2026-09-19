package com.hnieacm.achievement.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.achievement.constant.UserAchievementStatus;
import com.hnieacm.achievement.dto.AddUserAchievementRequest;
import com.hnieacm.achievement.entity.UserAchievement;
import com.hnieacm.achievement.entity.UserInfo;
import com.hnieacm.achievement.mapper.UserAchievementMapper;
import com.hnieacm.achievement.mapper.UserInfoMapper;
import com.hnieacm.achievement.service.UserAchievementService;
import com.hnieacm.achievement.vo.UserAchievementVo;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.common.util.PageParamUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 用户成就服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAchievementServiceImpl implements UserAchievementService {

    private final UserAchievementMapper userAchievementMapper;
    private final UserInfoMapper userInfoMapper;

    /**
     * @MethodName listByUid
     * @Param uid
     * @Param page
     * @Param pageSize
     * @Description 按uid列出
     * @Return @return {@link PageVo }<{@link UserAchievementVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    @Override
    public PageVo<UserAchievementVo> listByUid(String uid, int page, int pageSize) {
        uid = StrUtil.trim(uid);
        if (StrUtil.isBlank(uid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }
        PageParamUtils.validate(page, pageSize);

        Page<UserAchievement> mpPage = new Page<>(page, pageSize);
        Page<UserAchievement> result = userAchievementMapper.selectPage(
                mpPage,
                new LambdaQueryWrapper<UserAchievement>()
                        .eq(UserAchievement::getUid, uid)
                        .orderByDesc(UserAchievement::getAchieveTime)
                        .orderByDesc(UserAchievement::getId)
        );
        if (result.getRecords() == null || result.getRecords().isEmpty()) {
            return new PageVo<>(Collections.emptyList(), result.getTotal());
        }

        return new PageVo<>(result.getRecords().stream().map(this::toVo).toList(), result.getTotal());
    }

    /**
     * @MethodName add
     * @Param uid
     * @Param request
     * @Description 添加
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(String uid, AddUserAchievementRequest request) {
        uid = StrUtil.trim(uid);
        if (StrUtil.isBlank(uid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }

        long existed = userInfoMapper.selectCount(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUid, uid));
        if (existed == 0) {
            throw new BizException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }

        String content = StrUtil.trim(request.getContent());
        if (StrUtil.isBlank(content)) {
            throw new BizException(ResultCode.BAD_REQUEST, "content 不能为空");
        }

        String title = StrUtil.trim(request.getTitle());
        if (StrUtil.isBlank(title)) {
            throw new BizException(ResultCode.BAD_REQUEST, "title 不能为空");
        }

        UserAchievement achievement = new UserAchievement();
        achievement.setUid(uid);
        achievement.setTitle(title);
        achievement.setContent(content);
        achievement.setProofUrl(StrUtil.trimToNull(request.getProofUrl()));
        achievement.setAchieveTime(resolveLocalDateTime(request.getAchieveTime()));
        achievement.setStatus(UserAchievementStatus.APPROVED);

        userAchievementMapper.insert(achievement);

        log.info("Add user achievement, uid: {}, title: {}", uid, title);
    }

    /**
     * @MethodName delete
     * @Param uid
     * @Param achievementId
     * @Description 删除
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String uid, Long achievementId) {
        uid = StrUtil.trim(uid);
        if (StrUtil.isBlank(uid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }
        if (achievementId == null || achievementId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "achievementId 不合法");
        }

        long existed = userInfoMapper.selectCount(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUid, uid));
        if (existed == 0) {
            throw new BizException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }

        int deleted = userAchievementMapper.delete(
                new LambdaQueryWrapper<UserAchievement>()
                        .eq(UserAchievement::getId, achievementId)
                        .eq(UserAchievement::getUid, uid)
        );
        if (deleted == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "成就记录不存在");
        }
    }

    /**
     * @MethodName toVo
     * @Param record
     * @Description 转换为VO
     * @Return @return {@link UserAchievementVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    private UserAchievementVo toVo(UserAchievement record) {
        long timeMs = 0L;
        if (record.getAchieveTime() != null) {
            timeMs = record.getAchieveTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } else if (record.getGmtCreate() != null) {
            timeMs = record.getGmtCreate().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        Long time = timeMs == 0L ? null : timeMs;
        return new UserAchievementVo(
                record.getId(),
                record.getTitle(),
                record.getContent(),
                record.getProofUrl(),
                time,
                record.getStatus()
        );
    }

    /**
     * @MethodName resolveLocalDateTime
     * @Param epochMillis
      * @Description 解析本地日期时间（毫秒级时间戳）并转换为系统默认时区的日期时间
     * @Return @return {@link LocalDateTime }
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    private LocalDateTime resolveLocalDateTime(Long epochMillis) {
        if (epochMillis == null || epochMillis <= 0) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }
}
