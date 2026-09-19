package com.hnieacm.achievement.service;

import com.hnieacm.achievement.dto.AddUserAchievementRequest;
import com.hnieacm.achievement.vo.UserAchievementVo;
import com.hnieacm.common.dto.PageVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 用户成就服务
 */
public interface UserAchievementService {

    /**
     * 获取用户成就列表
     */
    PageVo<UserAchievementVo> listByUid(String uid, int page, int pageSize);

    /**
     * 添加用户成就
     */
    void add(String uid, AddUserAchievementRequest request);

    /**
     * 删除用户成就
     */
    void delete(String uid, Long achievementId);
}
