package com.hnieacm.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.user.service.UserMessageService;
import com.hnieacm.user.vo.UserMessageVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 本人站内收件箱接口。uid 一律取自服务端登录态，不接受请求体 ownerUid。
 */
@Tag(name = "站内消息（本人）")
@Validated
@RestController
@RequestMapping("/api/user/messages")
@RequiredArgsConstructor
public class UserMessageController {

    private final UserMessageService userMessageService;

    @Operation(summary = "本人收件箱分页")
    @GetMapping
    public Result<PageVo<UserMessageVo>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 必须大于等于 1") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") int pageSize,
            @RequestParam(required = false) Boolean unread) {
        String uid = StpUtil.getLoginIdAsString();
        return Result.success(userMessageService.listMyMessages(uid, page, pageSize, unread));
    }

    @Operation(summary = "本人未读数")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        String uid = StpUtil.getLoginIdAsString();
        return Result.success(userMessageService.countUnread(uid));
    }

    @Operation(summary = "标记本人消息已读")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(
            @PathVariable @Min(value = 1, message = "id 必须大于等于 1") Long id) {
        String uid = StpUtil.getLoginIdAsString();
        userMessageService.markRead(uid, id);
        return Result.success("已读", null);
    }

    @Operation(summary = "本人全部标记已读")
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        String uid = StpUtil.getLoginIdAsString();
        userMessageService.markAllRead(uid);
        return Result.success("已读", null);
    }

    @Operation(summary = "删除本人消息（软删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable @Min(value = 1, message = "id 必须大于等于 1") Long id) {
        String uid = StpUtil.getLoginIdAsString();
        userMessageService.deleteMessage(uid, id);
        return Result.success("删除成功", null);
    }
}
