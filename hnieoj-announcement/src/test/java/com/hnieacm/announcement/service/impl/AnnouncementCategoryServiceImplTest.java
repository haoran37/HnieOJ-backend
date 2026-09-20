package com.hnieacm.announcement.service.impl;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.announcement.constant.AnnouncementCategoryConstant;
import com.hnieacm.announcement.constant.AnnouncementStatusConstant;
import com.hnieacm.announcement.dto.AnnouncementCreateRequest;
import com.hnieacm.announcement.dto.AnnouncementUpdateRequest;
import com.hnieacm.announcement.entity.Announcement;
import com.hnieacm.announcement.mapper.AnnouncementMapper;
import com.hnieacm.announcement.vo.AnnouncementListVo;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import java.util.List;

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
 * @Description: 新闻分类回归：新增缺省 ANNOUNCEMENT、修改缺省保留原分类、非法分类拒绝、
 * 列表按 category 精确过滤/不传不过滤、公开列表仍只 ONLINE。
 */
class AnnouncementCategoryServiceImplTest {

    static {
        // 纯单元测试没有 MyBatis 会话，手动初始化 Announcement 的 TableInfo 以便检查 LambdaWrapper 生成的 SQL
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Announcement.class);
    }

    private AnnouncementMapper announcementMapper;
    private AnnouncementServiceImpl service;

    @BeforeEach
    void setUp() {
        announcementMapper = mock(AnnouncementMapper.class);
        service = new AnnouncementServiceImpl(announcementMapper);
    }

    private void runAsAdmin(Runnable action) {
        SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login("admin");
            action.run();
        });
    }

    @Test
    void createWithoutCategoryDefaultsToAnnouncement() {
        runAsAdmin(() -> service.createAnnouncement(createRequest(null)));

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementMapper).insert(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(AnnouncementCategoryConstant.ANNOUNCEMENT);
    }

    @Test
    void createWithNewsCategoryKeepsNews() {
        runAsAdmin(() -> service.createAnnouncement(createRequest(AnnouncementCategoryConstant.NEWS)));

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementMapper).insert(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(AnnouncementCategoryConstant.NEWS);
    }

    @Test
    void createWithInvalidCategoryRejected() {
        assertThatThrownBy(() -> runAsAdmin(() -> service.createAnnouncement(createRequest("OTHER"))))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(announcementMapper, never()).insert(any(Announcement.class));
    }

    @Test
    void updateWithoutCategoryKeepsOriginal() {
        Announcement existing = existing(AnnouncementCategoryConstant.NEWS);
        when(announcementMapper.selectById(31L)).thenReturn(existing);

        runAsAdmin(() -> service.updateAnnouncement(31L, updateRequest(null)));

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(AnnouncementCategoryConstant.NEWS);
    }

    @Test
    void updateWithBlankCategoryKeepsOriginal() {
        Announcement existing = existing(AnnouncementCategoryConstant.NEWS);
        when(announcementMapper.selectById(31L)).thenReturn(existing);

        runAsAdmin(() -> service.updateAnnouncement(31L, updateRequest("   ")));

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(AnnouncementCategoryConstant.NEWS);
    }

    @Test
    void updateWithValidCategoryChangesIt() {
        Announcement existing = existing(AnnouncementCategoryConstant.NEWS);
        when(announcementMapper.selectById(31L)).thenReturn(existing);

        runAsAdmin(() -> service.updateAnnouncement(31L, updateRequest(AnnouncementCategoryConstant.ANNOUNCEMENT)));

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(AnnouncementCategoryConstant.ANNOUNCEMENT);
    }

    @Test
    void updateWithInvalidCategoryRejected() {
        when(announcementMapper.selectById(31L)).thenReturn(existing(AnnouncementCategoryConstant.NEWS));

        assertThatThrownBy(() -> runAsAdmin(() -> service.updateAnnouncement(31L, updateRequest("BAD"))))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(announcementMapper, never()).updateById(any(Announcement.class));
    }

    @Test
    void invalidCategoryFilterRejectedOnPublicAndAdminList() {
        assertThatThrownBy(() -> service.listPublicAnnouncements(1, 10, null, "BAD"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        assertThatThrownBy(() -> service.listAdminAnnouncements(1, 10, null, null, "BAD"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
    }

    @Test
    void publicListWithCategoryBuildsCategoryFilterAndStaysOnline() {
        when(announcementMapper.selectPage(any(), any())).thenReturn(emptyPage());

        service.listPublicAnnouncements(1, 10, null, AnnouncementCategoryConstant.NEWS);

        assertThat(capturedTargetSql()).contains("category").contains("status");
    }

    @Test
    void publicListWithoutCategoryKeepsOldAllBehaviour() {
        when(announcementMapper.selectPage(any(), any())).thenReturn(emptyPage());

        service.listPublicAnnouncements(1, 10, null, null);

        assertThat(capturedTargetSql()).doesNotContain("category").contains("status");
    }

    @Test
    void listVoMapsCategory() {
        Announcement entity = existing(AnnouncementCategoryConstant.NEWS);
        Page<Announcement> page = new Page<>(1, 10);
        page.setRecords(List.of(entity));
        page.setTotal(1);
        when(announcementMapper.selectPage(any(), any())).thenReturn(page);

        PageVo<AnnouncementListVo> result = service.listPublicAnnouncements(1, 10, null, null);

        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getCategory()).isEqualTo(AnnouncementCategoryConstant.NEWS);
    }

    private String capturedTargetSql() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Announcement>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(announcementMapper).selectPage(any(), captor.capture());
        return captor.getValue().getTargetSql();
    }

    private Page<Announcement> emptyPage() {
        Page<Announcement> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        return page;
    }

    private AnnouncementCreateRequest createRequest(String category) {
        AnnouncementCreateRequest request = new AnnouncementCreateRequest();
        request.setTitle("title");
        request.setContent("content");
        request.setStatus(AnnouncementStatusConstant.ONLINE);
        request.setCategory(category);
        return request;
    }

    private AnnouncementUpdateRequest updateRequest(String category) {
        AnnouncementUpdateRequest request = new AnnouncementUpdateRequest();
        request.setTitle("title");
        request.setContent("content");
        request.setCategory(category);
        return request;
    }

    private Announcement existing(String category) {
        Announcement announcement = new Announcement();
        announcement.setId(31L);
        announcement.setTitle("title");
        announcement.setContent("content");
        announcement.setUid("admin");
        announcement.setStatus(AnnouncementStatusConstant.ONLINE);
        announcement.setCategory(category);
        return announcement;
    }
}
