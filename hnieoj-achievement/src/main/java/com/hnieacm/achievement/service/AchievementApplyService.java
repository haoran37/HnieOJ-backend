package com.hnieacm.achievement.service;

import com.hnieacm.achievement.vo.AchievementApplyAdminVo;
import com.hnieacm.achievement.vo.BatchAchievementApplyResultVo;
import com.hnieacm.common.dto.PageVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 成就认证申请服务
 */
public interface AchievementApplyService {

    /**
     * 提交成就认证申请
     */
    void submitApply(String loginUid, String title, String description, MultipartFile file);

    /**
     * 管理员分页查询申请列表
     */
    PageVo<AchievementApplyAdminVo> listForAdmin(int page, int pageSize, String keyword, String status, Long collegeId);

    /**
     * 通过成就认证申请
     */
    void approve(Long id);

    BatchAchievementApplyResultVo batchApprove(Iterable<Long> ids);

    /**
     * 驳回成就认证申请
     */
    void reject(Long id, String reason);
}
