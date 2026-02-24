package com.hnieacm.admin.service;

import com.hnieacm.admin.dto.AnnouncementCreateRequest;
import com.hnieacm.admin.dto.AnnouncementUpdateRequest;
import com.hnieacm.admin.dto.AnnouncementUpdateStatusRequest;
import com.hnieacm.admin.vo.AnnouncementDetailVo;
import com.hnieacm.admin.vo.AnnouncementListVo;
import com.hnieacm.common.dto.PageVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/24
 * @Description: 公告服务
 */
public interface AnnouncementService {

    /**
     * 前台分页查询公告列表（仅公开公告）
     */
    PageVo<AnnouncementListVo> listPublicAnnouncements(int page, int pageSize, String keyword);

    /**
     * 前台查询公告详情（仅公开公告）
     */
    AnnouncementDetailVo getPublicAnnouncementDetail(Long id);

    /**
     * 后台分页查询公告列表
     */
    PageVo<AnnouncementListVo> listAdminAnnouncements(int page, int pageSize, String keyword, Integer status);

    /**
     * 后台创建公告
     */
    void createAnnouncement(AnnouncementCreateRequest request);

    /**
     * 后台更新公告
     */
    void updateAnnouncement(Long id, AnnouncementUpdateRequest request);

    /**
     * 后台删除公告
     */
    void deleteAnnouncement(Long id);

    /**
     * 后台更新公告状态
     */
    void updateAnnouncementStatus(Long id, AnnouncementUpdateStatusRequest request);
}
