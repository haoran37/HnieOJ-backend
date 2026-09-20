package com.hnieacm.announcement.service.impl;

import com.hnieacm.announcement.constant.AnnouncementStatusConstant;
import com.hnieacm.announcement.entity.Announcement;
import com.hnieacm.announcement.mapper.AnnouncementMapper;
import com.hnieacm.announcement.vo.AnnouncementDetailVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 管理端公告详情回归：下线/上线公告都必须返回真实正文与状态，管理读取不得写库；
 * 不存在返回 NOT_FOUND；公开详情仍拒绝下线公告。
 */
class AdminAnnouncementServiceImplDetailTest {

    private AnnouncementMapper announcementMapper;
    private AnnouncementServiceImpl service;

    @BeforeEach
    void setUp() {
        announcementMapper = mock(AnnouncementMapper.class);
        service = new AnnouncementServiceImpl(announcementMapper);
    }

    private Announcement announcement(int status, String content) {
        Announcement announcement = new Announcement();
        announcement.setId(31L);
        announcement.setTitle("notice title");
        announcement.setContent(content);
        announcement.setUid("admin");
        announcement.setStatus(status);
        return announcement;
    }

    @Test
    void adminDetailReturnsRealContentAndStatusForOfflineAnnouncement() {
        when(announcementMapper.selectById(31L))
                .thenReturn(announcement(AnnouncementStatusConstant.OFFLINE, "offline body"));

        AnnouncementDetailVo detail = service.getAdminAnnouncementDetail(31L);

        assertThat(detail.getId()).isEqualTo(31L);
        assertThat(detail.getTitle()).isEqualTo("notice title");
        assertThat(detail.getContent()).isEqualTo("offline body");
        assertThat(detail.getStatus()).isEqualTo(AnnouncementStatusConstant.OFFLINE);
        // 管理读取不得写库
        verify(announcementMapper, never()).updateById(any(Announcement.class));
    }

    @Test
    void adminDetailReturnsRealContentAndStatusForOnlineAnnouncement() {
        when(announcementMapper.selectById(31L))
                .thenReturn(announcement(AnnouncementStatusConstant.ONLINE, "online body"));

        AnnouncementDetailVo detail = service.getAdminAnnouncementDetail(31L);

        assertThat(detail.getContent()).isEqualTo("online body");
        assertThat(detail.getStatus()).isEqualTo(AnnouncementStatusConstant.ONLINE);
        verify(announcementMapper, never()).updateById(any(Announcement.class));
    }

    @Test
    void missingAnnouncementReturnsNotFoundBusinessCode() {
        when(announcementMapper.selectById(999999999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getAdminAnnouncementDetail(999999999L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND);
    }

    @Test
    void publicDetailStillHidesOfflineAnnouncement() {
        when(announcementMapper.selectById(31L))
                .thenReturn(announcement(AnnouncementStatusConstant.OFFLINE, "offline body"));

        assertThatThrownBy(() -> service.getPublicAnnouncementDetail(31L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND);
    }

    @Test
    void publicDetailStillReturnsOnlineAnnouncement() {
        when(announcementMapper.selectById(31L))
                .thenReturn(announcement(AnnouncementStatusConstant.ONLINE, "online body"));

        AnnouncementDetailVo detail = service.getPublicAnnouncementDetail(31L);

        assertThat(detail.getContent()).isEqualTo("online body");
        assertThat(detail.getStatus()).isEqualTo(AnnouncementStatusConstant.ONLINE);
    }
}
