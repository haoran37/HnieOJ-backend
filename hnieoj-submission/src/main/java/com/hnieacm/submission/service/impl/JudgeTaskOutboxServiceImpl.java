package com.hnieacm.submission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.submission.constant.JudgeTaskOutboxStatusConstant;
import com.hnieacm.submission.dto.JudgeTaskOutboxQueryRequest;
import com.hnieacm.submission.entity.JudgeTaskOutbox;
import com.hnieacm.submission.mapper.JudgeTaskOutboxMapper;
import com.hnieacm.submission.service.JudgeTaskMessagePublisher;
import com.hnieacm.submission.service.JudgeTaskOutboxService;
import com.hnieacm.submission.vo.JudgeTaskOutboxVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题任务 outbox 服务实现
 */
@Service
@RequiredArgsConstructor
public class JudgeTaskOutboxServiceImpl implements JudgeTaskOutboxService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> STATUS_SET = Set.of(
            JudgeTaskOutboxStatusConstant.PENDING,
            JudgeTaskOutboxStatusConstant.PROCESSING,
            JudgeTaskOutboxStatusConstant.SENT,
            JudgeTaskOutboxStatusConstant.FAILED,
            JudgeTaskOutboxStatusConstant.EXHAUSTED
    );

    private final JudgeTaskOutboxMapper outboxMapper;
    private final JudgeTaskMessagePublisher judgeTaskMessagePublisher;

    /**
     * @MethodName list
     * @Param request
     * @Description 分页查询判题任务 outbox 记录
     * @Return @return {@link PageVo }<{@link JudgeTaskOutboxVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public PageVo<JudgeTaskOutboxVo> list(JudgeTaskOutboxQueryRequest request) {
        int page = normalizePage(request == null ? null : request.getPage());
        int pageSize = normalizePageSize(request == null ? null : request.getPageSize());
        LambdaQueryWrapper<JudgeTaskOutbox> wrapper = new LambdaQueryWrapper<JudgeTaskOutbox>()
                .orderByDesc(JudgeTaskOutbox::getGmtCreate)
                .orderByDesc(JudgeTaskOutbox::getId);

        String status = normalizeStatus(request == null ? null : request.getStatus(), false);
        if (status != null) {
            wrapper.eq(JudgeTaskOutbox::getStatus, status);
        }
        String submissionId = StrUtil.trimToNull(request == null ? null : request.getSubmissionId());
        if (submissionId != null) {
            wrapper.eq(JudgeTaskOutbox::getSubmissionId, submissionId);
        }
        String judgeTaskId = StrUtil.trimToNull(request == null ? null : request.getJudgeTaskId());
        if (judgeTaskId != null) {
            wrapper.eq(JudgeTaskOutbox::getJudgeTaskId, judgeTaskId);
        }

        Page<JudgeTaskOutbox> pageResult = outboxMapper.selectPage(new Page<>(page, pageSize), wrapper);
        if (pageResult.getRecords() == null || pageResult.getRecords().isEmpty()) {
            return new PageVo<>(Collections.emptyList(), pageResult.getTotal());
        }
        return new PageVo<>(pageResult.getRecords().stream().map(this::toVo).toList(), pageResult.getTotal());
    }

    /**
     * @MethodName retry
     * @Param id
     * @Description 手动重试指定 outbox 记录
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public void retry(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 必须大于 0");
        }
        JudgeTaskOutbox outbox = outboxMapper.selectById(id);
        if (outbox == null) {
            throw new BizException(ResultCode.NOT_FOUND, "outbox 记录不存在");
        }
        if (JudgeTaskOutboxStatusConstant.SENT.equals(outbox.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "已发送的 outbox 不能重试");
        }
        judgeTaskMessagePublisher.retryOutbox(id);
    }

    /**
     * @MethodName toVo
     * @Param outbox
     * @Description 转换 outbox 展示对象
     * @Return @return {@link JudgeTaskOutboxVo }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private JudgeTaskOutboxVo toVo(JudgeTaskOutbox outbox) {
        JudgeTaskOutboxVo vo = new JudgeTaskOutboxVo();
        vo.setId(outbox.getId());
        vo.setMessageId(outbox.getMessageId());
        vo.setJudgeTaskId(outbox.getJudgeTaskId());
        vo.setSubmissionId(outbox.getSubmissionId());
        vo.setStatus(outbox.getStatus());
        vo.setRetryCount(outbox.getRetryCount());
        vo.setPublishAttempt(outbox.getPublishAttempt());
        vo.setMaxRetryCount(outbox.getMaxRetryCount());
        vo.setNextRetryTime(outbox.getNextRetryTime());
        vo.setSentTime(outbox.getSentTime());
        vo.setLastError(outbox.getLastError());
        vo.setGmtCreate(outbox.getGmtCreate());
        vo.setGmtModified(outbox.getGmtModified());
        return vo;
    }

    /**
     * @MethodName normalizeStatus
     * @Param status
     * @Param required
     * @Description 规范化并校验 outbox 状态
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private String normalizeStatus(String status, boolean required) {
        String normalized = StrUtil.trimToNull(status);
        if (normalized == null) {
            if (required) {
                throw new BizException(ResultCode.BAD_REQUEST, "status 不能为空");
            }
            return null;
        }
        if (!STATUS_SET.contains(normalized)) {
            throw new BizException(ResultCode.BAD_REQUEST, "status 不合法");
        }
        return normalized;
    }

    /**
     * @MethodName normalizePage
     * @Param page
     * @Description 规范化页码
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private int normalizePage(Integer page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 必须大于 0");
        }
        return page;
    }

    /**
     * @MethodName normalizePageSize
     * @Param pageSize
     * @Description 规范化每页数量
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "pageSize 必须大于 0");
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ResultCode.BAD_REQUEST, "pageSize 不能大于 " + MAX_PAGE_SIZE);
        }
        return pageSize;
    }
}
