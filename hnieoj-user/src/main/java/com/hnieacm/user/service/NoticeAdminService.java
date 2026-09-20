package com.hnieacm.user.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.user.dto.NoticeSaveRequest;
import com.hnieacm.user.vo.UserNoticeDetailVo;
import com.hnieacm.user.vo.UserNoticeListVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 定向通知管理服务（ADMIN/ROOT）
 */
public interface NoticeAdminService {

    /**
     * 分页查询通知（草稿+已发布），支持标题 keyword 与 status 过滤。
     */
    PageVo<UserNoticeListVo> listNotices(int page, int pageSize, String keyword, String status);

    /**
     * 通知详情，返回已保存的目标设置（targetType + targetIds）。
     */
    UserNoticeDetailVo getNotice(Long id);

    /**
     * 新建通知草稿，返回通知 id。
     */
    Long createNotice(NoticeSaveRequest request, String creatorUid);

    /**
     * 编辑草稿通知；已发布通知拒绝编辑正文与目标。
     */
    void updateNotice(Long id, NoticeSaveRequest request);

    /**
     * 删除通知管理记录，不级联删除已送达的 user_message。
     */
    void deleteNotice(Long id);

    /**
     * 发布通知：锁定通知行，按当时班级/用户快照写入消息，原子置为 PUBLISHED；重复发布幂等。
     */
    void publishNotice(Long id);
}
