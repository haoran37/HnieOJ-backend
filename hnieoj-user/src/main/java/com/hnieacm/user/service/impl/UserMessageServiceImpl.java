package com.hnieacm.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.entity.UserMessage;
import com.hnieacm.user.mapper.UserMessageMapper;
import com.hnieacm.user.service.UserMessageService;
import com.hnieacm.user.vo.UserMessageVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 本人站内消息服务实现
 */
@Service
@RequiredArgsConstructor
public class UserMessageServiceImpl implements UserMessageService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserMessageMapper userMessageMapper;

    @Override
    public PageVo<UserMessageVo> listMyMessages(String uid, int page, int pageSize, Boolean unread) {
        validatePage(page, pageSize);

        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getRecipientUid, uid)
                .isNull(UserMessage::getDeletedAt);
        if (Boolean.TRUE.equals(unread)) {
            wrapper.isNull(UserMessage::getReadAt);
        }
        wrapper.orderByDesc(UserMessage::getCreatedAt, UserMessage::getId);

        Page<UserMessage> mpPage = new Page<>(page, pageSize);
        Page<UserMessage> result = userMessageMapper.selectPage(mpPage, wrapper);
        List<UserMessageVo> list = result.getRecords().stream().map(this::toVo).toList();
        return new PageVo<>(list, result.getTotal());
    }

    @Override
    public long countUnread(String uid) {
        Long count = userMessageMapper.selectCount(
                new LambdaQueryWrapper<UserMessage>()
                        .eq(UserMessage::getRecipientUid, uid)
                        .isNull(UserMessage::getDeletedAt)
                        .isNull(UserMessage::getReadAt)
        );
        return count == null ? 0L : count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(String uid, Long messageId) {
        UserMessage message = requireOwnMessage(uid, messageId);
        if (message.getReadAt() == null) {
            message.setReadAt(LocalDateTime.now());
            userMessageMapper.updateById(message);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(String uid) {
        userMessageMapper.update(
                null,
                new LambdaUpdateWrapper<UserMessage>()
                        .eq(UserMessage::getRecipientUid, uid)
                        .isNull(UserMessage::getDeletedAt)
                        .isNull(UserMessage::getReadAt)
                        .set(UserMessage::getReadAt, LocalDateTime.now())
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessage(String uid, Long messageId) {
        UserMessage message = requireOwnMessage(uid, messageId);
        if (message.getDeletedAt() == null) {
            message.setDeletedAt(LocalDateTime.now());
            userMessageMapper.updateById(message);
        }
    }

    private UserMessage requireOwnMessage(String uid, Long messageId) {
        if (messageId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 不能为空");
        }
        UserMessage message = userMessageMapper.selectOne(
                new LambdaQueryWrapper<UserMessage>()
                        .eq(UserMessage::getId, messageId)
                        .eq(UserMessage::getRecipientUid, uid)
        );
        // 非本人消息一律按不存在处理，避免暴露他人消息是否存在。
        if (message == null) {
            throw new BizException(ResultCode.NOT_FOUND, "消息不存在");
        }
        return message;
    }

    private void validatePage(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 和 pageSize 必须大于 0");
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ResultCode.BAD_REQUEST, "pageSize 不能超过 100");
        }
    }

    private UserMessageVo toVo(UserMessage message) {
        UserMessageVo vo = new UserMessageVo();
        vo.setId(message.getId());
        vo.setNoticeId(message.getNoticeId());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setReadAt(message.getReadAt());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }
}
