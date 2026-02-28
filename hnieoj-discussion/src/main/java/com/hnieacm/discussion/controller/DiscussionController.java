package com.hnieacm.discussion.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.discussion.dto.CreateDiscussionAnswerRequest;
import com.hnieacm.discussion.dto.CreateDiscussionCommentRequest;
import com.hnieacm.discussion.dto.CreateDiscussionRequest;
import com.hnieacm.discussion.dto.DiscussionVoteRequest;
import com.hnieacm.discussion.service.DiscussionService;
import com.hnieacm.discussion.vo.DiscussionCreateVo;
import com.hnieacm.discussion.vo.DiscussionDetailVo;
import com.hnieacm.discussion.vo.DiscussionListVo;
import com.hnieacm.discussion.vo.DiscussionRelatedVo;
import com.hnieacm.discussion.vo.DiscussionVoteVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论模块对外接口
 */
@Tag(name = "讨论模块")
@Validated
@SaCheckLogin
@RestController
@RequestMapping("/api/discussions")
@RequiredArgsConstructor
public class DiscussionController {

    private final DiscussionService discussionService;

    @Operation(summary = "获取讨论列表")
    @GetMapping
    public Result<PageVo<DiscussionListVo>> list(
            @RequestParam @Min(value = 1, message = "page 必须大于等于 1") int page,
            @RequestParam @Min(value = 1, message = "pageSize 必须大于等于 1") int pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort) {
        return Result.success(discussionService.listDiscussions(page, pageSize, category, keyword, sort));
    }

    @Operation(summary = "获取讨论详情")
    @GetMapping("/{id}")
    public Result<DiscussionDetailVo> detail(@PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long discussionId) {
        return Result.success(discussionService.getDiscussionDetail(discussionId));
    }

    @Operation(summary = "点赞/点踩")
    @PostMapping("/{type}/{id}/vote")
    public Result<DiscussionVoteVo> vote(
            @PathVariable String type,
            @PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long targetId,
            @Valid @RequestBody DiscussionVoteRequest request) {
        return Result.success(discussionService.vote(type, targetId, request.getDirection()));
    }

    @Operation(summary = "新建讨论")
    @PostMapping
    public Result<DiscussionCreateVo> createDiscussion(@Valid @RequestBody CreateDiscussionRequest request) {
        return Result.success("发布成功", discussionService.createDiscussion(request));
    }

    @Operation(summary = "发布回答")
    @PostMapping("/{postId}/answers")
    public Result<DiscussionCreateVo> createAnswer(
            @PathVariable @Min(value = 1, message = "postId 必须大于等于 1") Long postId,
            @Valid @RequestBody CreateDiscussionAnswerRequest request) {
        return Result.success("发布成功", discussionService.createAnswer(postId, request));
    }

    @Operation(summary = "发布评论")
    @PostMapping("/answers/{answerId}/comments")
    public Result<DiscussionCreateVo> createComment(
            @PathVariable @Min(value = 1, message = "answerId 必须大于等于 1") Long answerId,
            @Valid @RequestBody CreateDiscussionCommentRequest request) {
        return Result.success("发布成功", discussionService.createComment(answerId, request));
    }

    @Operation(summary = "获取相关讨论")
    @GetMapping("/related")
    public Result<List<DiscussionRelatedVo>> related(@RequestParam String problemCode,
                                                     @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                     @RequestParam(value = "limit", required = false) Integer limit) {
        Integer queryLimit = pageSize == null ? limit : pageSize;
        return Result.success(discussionService.listRelatedDiscussions(problemCode, queryLimit));
    }
}
