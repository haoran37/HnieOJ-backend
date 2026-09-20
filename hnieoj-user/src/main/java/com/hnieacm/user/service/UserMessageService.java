package com.hnieacm.user.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.user.vo.UserMessageVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 本人站内消息服务。所有操作都必须由服务端登录态 uid 驱动，不接受客户端 ownerUid。
 */
public interface UserMessageService {

    /**
     * 本人收件箱分页；unread 为 true 时仅返回未读。
     */
    PageVo<UserMessageVo> listMyMessages(String uid, int page, int pageSize, Boolean unread);

    /**
     * 本人未读数（排除已删除）。
     */
    long countUnread(String uid);

    /**
     * 标记本人消息已读；重复标记幂等，非本人消息按不存在处理。
     */
    void markRead(String uid, Long messageId);

    /**
     * 本人全部未读标记已读。
     */
    void markAllRead(String uid);

    /**
     * 本人软删除消息；重复删除幂等，非本人消息按不存在处理。
     */
    void deleteMessage(String uid, Long messageId);
}
