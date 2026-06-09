package com.hnieacm.announcement.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.announcement.constant.AnnouncementStatusConstant;
import com.hnieacm.announcement.dto.AnnouncementCreateRequest;
import com.hnieacm.announcement.dto.AnnouncementUpdateRequest;
import com.hnieacm.announcement.dto.AnnouncementUpdateStatusRequest;
import com.hnieacm.announcement.entity.Announcement;
import com.hnieacm.announcement.mapper.AnnouncementMapper;
import com.hnieacm.announcement.service.AnnouncementService;
import com.hnieacm.announcement.vo.AnnouncementDetailVo;
import com.hnieacm.announcement.vo.AnnouncementListVo;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.common.util.PageParamUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/24
 * @Description: 公告服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementMapper announcementMapper;

    /**
     * @MethodName listPublicAnnouncements
     * @Param page
     * @Param pageSize
     * @Param keyword
     * @Description 公告列表
     * @Return @return {@link PageVo }<{@link AnnouncementListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    @Override
    public PageVo<AnnouncementListVo> listPublicAnnouncements(int page, int pageSize, String keyword) {
        LambdaQueryWrapper<Announcement> wrapper = buildListQueryWrapper(keyword, AnnouncementStatusConstant.ONLINE);
        return queryAnnouncementPage(page, pageSize, wrapper);
    }

    /**
     * @MethodName getPublicAnnouncementDetail
     * @Param id
     * @Description 获取公告详情
     * @Return @return {@link AnnouncementDetailVo }
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    @Override
    public AnnouncementDetailVo getPublicAnnouncementDetail(Long id) {
        Announcement announcement = requireAnnouncement(id);
        // 前台只允许查看已上线公告，未上线按不存在处理
        if (!AnnouncementStatusConstant.isOnline(announcement.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "公告不存在");
        }
        return toDetailVo(announcement);
    }

    /**
     * @MethodName listAdminAnnouncements
     * @Param page
     * @Param pageSize
     * @Param keyword
     * @Param status
     * @Description 公告列表（管理员）
     * @Return @return {@link PageVo }<{@link AnnouncementListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    @Override
    public PageVo<AnnouncementListVo> listAdminAnnouncements(int page, int pageSize, String keyword, Integer status) {
        validateStatus(status, true);
        LambdaQueryWrapper<Announcement> wrapper = buildListQueryWrapper(keyword, status);
        return queryAnnouncementPage(page, pageSize, wrapper);
    }

    /**
     * @MethodName createAnnouncement
     * @Param request
     * @Description 创建公告
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createAnnouncement(AnnouncementCreateRequest request) {
        Announcement announcement = new Announcement();
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcement.setUid(StpUtil.getLoginIdAsString());
        announcement.setStatus(resolveStatus(request.getStatus()));
        announcementMapper.insert(announcement);
        log.info("Announcement created, id: {}, operator: {}", announcement.getId(), announcement.getUid());
    }

    /**
     * @MethodName updateAnnouncement
     * @Param id
     * @Param request
     * @Description 更新公告
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAnnouncement(Long id, AnnouncementUpdateRequest request) {
        Announcement announcement = requireAnnouncement(id);
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        if (request.getStatus() != null) {
            validateStatus(request.getStatus(), false);
            announcement.setStatus(request.getStatus());
        }
        announcementMapper.updateById(announcement);
        log.info("Announcement updated, id: {}, operator: {}", id, StpUtil.getLoginIdAsString());
    }

    /**
     * @MethodName deleteAnnouncement
     * @Param id
     * @Description 删除公告
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAnnouncement(Long id) {
        requireAnnouncement(id);
        announcementMapper.deleteById(id);
        log.info("Announcement deleted, id: {}, operator: {}", id, StpUtil.getLoginIdAsString());
    }

    /**
     * @MethodName updateAnnouncementStatus
     * @Param id
     * @Param request
     * @Description 更新公告状态
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAnnouncementStatus(Long id, AnnouncementUpdateStatusRequest request) {
        Announcement announcement = requireAnnouncement(id);
        validateStatus(request.getStatus(), false);
        announcement.setStatus(request.getStatus());
        announcementMapper.updateById(announcement);
        log.info(
                "Announcement status updated, id: {}, status: {}, operator: {}",
                id,
                request.getStatus(),
                StpUtil.getLoginIdAsString()
        );
    }

    /**
     * @MethodName queryAnnouncementPage
     * @Param page
     * @Param pageSize
     * @Param wrapper
     * @Description 统一处理分页查询，避免列表接口重复组装分页结构
     * @Return @return {@link PageVo }<{@link AnnouncementListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    private PageVo<AnnouncementListVo> queryAnnouncementPage(
            int page,
            int pageSize,
            LambdaQueryWrapper<Announcement> wrapper
    ) {
        PageParamUtils.validate(page, pageSize);

        Page<Announcement> mpPage = new Page<>(page, pageSize);
        IPage<Announcement> result = announcementMapper.selectPage(mpPage, wrapper);
        List<AnnouncementListVo> list = result.getRecords().stream().map(this::toListVo).toList();
        return new PageVo<>(list, result.getTotal());
    }

    /**
     * @MethodName buildListQueryWrapper
     * @Param keyword
     * @Param status
     * @Description 列表查询统一条件构建，避免 Controller/Service 多处拼接查询逻辑
     * @Return @return {@link LambdaQueryWrapper }<{@link Announcement }>
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    private LambdaQueryWrapper<Announcement> buildListQueryWrapper(String keyword, Integer status) {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(
                Announcement::getId,
                Announcement::getTitle,
                Announcement::getUid,
                Announcement::getStatus,
                Announcement::getGmtCreate,
                Announcement::getGmtModified
        );

        if (status != null) {
            wrapper.eq(Announcement::getStatus, status);
        }

        if (StringUtils.hasText(keyword)) {
            wrapper.like(Announcement::getTitle, keyword.trim());
        }

        wrapper.orderByDesc(Announcement::getGmtCreate, Announcement::getId);
        return wrapper;
    }

    /**
     * @MethodName requireAnnouncement
     * @Param id
     * @Description 获取公告
     * @Return @return {@link Announcement }
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    private Announcement requireAnnouncement(Long id) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BizException(ResultCode.NOT_FOUND, "公告不存在");
        }
        return announcement;
    }

    /**
     * @MethodName resolveStatus
     * @Param status
     * @Description 解析状态
     * @Return @return {@link Integer }
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    private Integer resolveStatus(Integer status) {
        if (status == null) {
            return AnnouncementStatusConstant.ONLINE;
        }
        validateStatus(status, false);
        return status;
    }

    /**
     * @MethodName validateStatus
     * @Param status
     * @Param allowNull
     * @Description 验证状态
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    private void validateStatus(Integer status, boolean allowNull) {
        if (status == null) {
            if (allowNull) {
                return;
            }
            throw new BizException(ResultCode.BAD_REQUEST, "status 不能为空");
        }

        if (!AnnouncementStatusConstant.isValid(status)) {
            throw new BizException(ResultCode.BAD_REQUEST, "status 只能为 0 或 1");
        }
    }

    /**
     * @MethodName toListVo
     * @Param announcement
     * @Description 列表vo
     * @Return @return {@link AnnouncementListVo }
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    private AnnouncementListVo toListVo(Announcement announcement) {
        AnnouncementListVo vo = new AnnouncementListVo();
        vo.setId(announcement.getId());
        vo.setTitle(announcement.getTitle());
        vo.setUid(announcement.getUid());
        vo.setStatus(announcement.getStatus());
        vo.setGmtCreate(announcement.getGmtCreate());
        vo.setGmtModified(announcement.getGmtModified());
        return vo;
    }

    /**
     * @MethodName toDetailVo
     * @Param announcement
     * @Description 详情vo
     * @Return @return {@link AnnouncementDetailVo }
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    private AnnouncementDetailVo toDetailVo(Announcement announcement) {
        AnnouncementDetailVo vo = new AnnouncementDetailVo();
        vo.setId(announcement.getId());
        vo.setTitle(announcement.getTitle());
        vo.setContent(announcement.getContent());
        vo.setUid(announcement.getUid());
        vo.setStatus(announcement.getStatus());
        vo.setGmtCreate(announcement.getGmtCreate());
        vo.setGmtModified(announcement.getGmtModified());
        return vo;
    }
}
