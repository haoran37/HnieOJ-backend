package com.hnieacm.announcement.service;

import com.hnieacm.announcement.dto.AnnouncementCreateRequest;
import com.hnieacm.announcement.dto.AnnouncementUpdateRequest;
import com.hnieacm.announcement.dto.AnnouncementUpdateStatusRequest;
import com.hnieacm.announcement.vo.AnnouncementDetailVo;
import com.hnieacm.announcement.vo.AnnouncementListVo;
import com.hnieacm.common.dto.PageVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/24
 * @Description: 公告服务
 */
public interface AnnouncementService {

    /**
     * 前台分页查询公告列表（仅公开公告）；category 为空保持旧全部行为，非空精确过滤
     */
    PageVo<AnnouncementListVo> listPublicAnnouncements(int page, int pageSize, String keyword, String category);

    /**
     * 前台查询公告详情（仅公开公告）
     */
    AnnouncementDetailVo getPublicAnnouncementDetail(Long id);

    /**
     * 后台查询公告详情（含下线公告，返回真实正文与状态）
     */
    AnnouncementDetailVo getAdminAnnouncementDetail(Long id);

    /**
     * 后台分页查询公告列表；category 为空不额外过滤，非空精确过滤
     */
    PageVo<AnnouncementListVo> listAdminAnnouncements(int page, int pageSize, String keyword, Integer status,
                                                      String category);

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
