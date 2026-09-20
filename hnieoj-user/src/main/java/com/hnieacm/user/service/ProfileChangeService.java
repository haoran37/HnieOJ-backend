package com.hnieacm.user.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.user.dto.ProfileChangeCreateRequest;
import com.hnieacm.user.vo.ProfileChangeVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 用户身份资料变更申请服务
 */
public interface ProfileChangeService {

    /**
     * 本人提交变更申请；用户行锁保证同一用户仅存在一条待审申请。
     */
    void createChangeRequest(String uid, ProfileChangeCreateRequest request);

    /**
     * 本人变更申请分页。
     */
    PageVo<ProfileChangeVo> listMyChangeRequests(String uid, int page, int pageSize);

    /**
     * 管理员变更申请分页，支持 status 与 keyword（uid/reason）过滤。
     */
    PageVo<ProfileChangeVo> listAdminChangeRequests(int page, int pageSize, String status, String keyword);

    /**
     * 审核通过：校验原值仍一致后原子更新身份与审核信息；重复同结果幂等，反向审核拒绝。
     */
    void approve(Long id, String reason, String reviewerUid);

    /**
     * 审核驳回：仅更新申请状态与驳回信息；重复同结果幂等，反向审核拒绝。
     */
    void reject(Long id, String reason, String reviewerUid);
}
