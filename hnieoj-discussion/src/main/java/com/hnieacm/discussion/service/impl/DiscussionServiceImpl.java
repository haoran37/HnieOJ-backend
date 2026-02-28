package com.hnieacm.discussion.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.discussion.constant.DiscussionCategoryConstant;
import com.hnieacm.discussion.constant.DiscussionRoleConstant;
import com.hnieacm.discussion.constant.DiscussionSortConstant;
import com.hnieacm.discussion.constant.DiscussionStatusConstant;
import com.hnieacm.discussion.constant.DiscussionTargetTypeConstant;
import com.hnieacm.discussion.constant.DiscussionTopPriorityConstant;
import com.hnieacm.discussion.constant.DiscussionVoteDirectionConstant;
import com.hnieacm.discussion.dto.AdminUpdateDiscussionRequest;
import com.hnieacm.discussion.dto.CreateDiscussionAnswerRequest;
import com.hnieacm.discussion.dto.CreateDiscussionCommentRequest;
import com.hnieacm.discussion.dto.CreateDiscussionRequest;
import com.hnieacm.discussion.entity.Discussion;
import com.hnieacm.discussion.entity.DiscussionAnswer;
import com.hnieacm.discussion.entity.DiscussionComment;
import com.hnieacm.discussion.entity.DiscussionLike;
import com.hnieacm.discussion.feign.ProblemInternalFeignClient;
import com.hnieacm.discussion.mapper.DiscussionAnswerMapper;
import com.hnieacm.discussion.mapper.DiscussionCommentMapper;
import com.hnieacm.discussion.mapper.DiscussionLikeMapper;
import com.hnieacm.discussion.mapper.DiscussionMapper;
import com.hnieacm.discussion.service.DiscussionService;
import com.hnieacm.discussion.vo.AdminDiscussionListVo;
import com.hnieacm.discussion.vo.DiscussionAnswerVo;
import com.hnieacm.discussion.vo.DiscussionCommentVo;
import com.hnieacm.discussion.vo.DiscussionCreateVo;
import com.hnieacm.discussion.vo.DiscussionDetailVo;
import com.hnieacm.discussion.vo.DiscussionListVo;
import com.hnieacm.discussion.vo.DiscussionPostVo;
import com.hnieacm.discussion.vo.DiscussionRelatedVo;
import com.hnieacm.discussion.vo.DiscussionVoteVo;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscussionServiceImpl implements DiscussionService {

    private static final int DESCRIPTION_MAX_LENGTH = 200;

    private final DiscussionMapper discussionMapper;
    private final DiscussionAnswerMapper discussionAnswerMapper;
    private final DiscussionCommentMapper discussionCommentMapper;
    private final DiscussionLikeMapper discussionLikeMapper;
    private final ProblemInternalFeignClient problemInternalFeignClient;

    /**
     * @MethodName listDiscussions
     * @Param page
     * @Param pageSize
     * @Param category
     * @Param keyword
     * @Param sort
     * @Description 讨论列表
     * @Return @return {@link PageVo }<{@link DiscussionListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @Override
    public PageVo<DiscussionListVo> listDiscussions(int page, int pageSize, String category, String keyword, String sort) {
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 和 pageSize 必须大于 0");
        }

        String normalizedCategory = DiscussionCategoryConstant.normalizeWithAll(category);
        String normalizedSort = DiscussionSortConstant.normalize(sort);
        String normalizedKeyword = trimToNull(keyword);

        LambdaQueryWrapper<Discussion> wrapper = new LambdaQueryWrapper<Discussion>()
                .eq(Discussion::getStatus, DiscussionStatusConstant.NORMAL)
                .orderByDesc(Discussion::getTopPriority);
        if (!DiscussionCategoryConstant.ALL.equals(normalizedCategory)) {
            wrapper.eq(Discussion::getCategory, normalizedCategory);
        }
        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(Discussion::getTitle, normalizedKeyword)
                    .or()
                    .like(Discussion::getDescription, normalizedKeyword)
                    .or()
                    .like(Discussion::getContent, normalizedKeyword));
        }
        if (DiscussionSortConstant.HOT.equals(normalizedSort)) {
            wrapper.orderByDesc(Discussion::getLikeNum)
                    .orderByDesc(Discussion::getViewNum)
                    .orderByDesc(Discussion::getId);
        } else {
            wrapper.orderByDesc(Discussion::getGmtCreate)
                    .orderByDesc(Discussion::getId);
        }

        Page<Discussion> pageParam = new Page<>(page, pageSize);
        Page<Discussion> pageResult = discussionMapper.selectPage(pageParam, wrapper);
        List<Discussion> records = pageResult.getRecords();
        if (records == null || records.isEmpty()) {
            return new PageVo<>(Collections.emptyList(), pageResult.getTotal());
        }

        Map<Long, Long> answerCountMap = queryAnswerCountMap(records);
        List<DiscussionListVo> list = records.stream()
                .map(post -> toDiscussionListVo(post, answerCountMap.getOrDefault(post.getId(), 0L)))
                .toList();
        return new PageVo<>(list, pageResult.getTotal());
    }

    /**
     * @MethodName getDiscussionDetail
     * @Param discussionId
     * @Description 获取讨论详情
     * @Return @return {@link DiscussionDetailVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiscussionDetailVo getDiscussionDetail(Long discussionId) {
        Discussion post = queryNormalDiscussion(discussionId);

        Integer latestViewNum = (post.getViewNum() == null ? 0 : post.getViewNum()) + 1;
        Discussion updateView = new Discussion();
        updateView.setId(post.getId());
        updateView.setViewNum(latestViewNum);
        discussionMapper.updateById(updateView);
        post.setViewNum(latestViewNum);

        List<DiscussionAnswer> answers = discussionAnswerMapper.selectList(new LambdaQueryWrapper<DiscussionAnswer>()
                .eq(DiscussionAnswer::getDid, post.getId())
                .eq(DiscussionAnswer::getStatus, DiscussionStatusConstant.NORMAL)
                .orderByAsc(DiscussionAnswer::getGmtCreate)
                .orderByAsc(DiscussionAnswer::getId));

        Map<Long, List<DiscussionCommentVo>> commentMap = queryCommentMap(answers);
        List<DiscussionAnswerVo> answerVos = answers.stream().map(answer -> {
            DiscussionAnswerVo vo = new DiscussionAnswerVo();
            vo.setId(answer.getId());
            vo.setDid(answer.getDid());
            vo.setContent(answer.getContent());
            vo.setUid(answer.getUid());
            vo.setAuthor(answer.getAuthor());
            vo.setLikeNum(answer.getLikeNum());
            vo.setGmtCreate(answer.getGmtCreate());
            vo.setComments(commentMap.getOrDefault(answer.getId(), Collections.emptyList()));
            return vo;
        }).toList();

        DiscussionPostVo postVo = toDiscussionPostVo(post);

        DiscussionDetailVo detailVo = new DiscussionDetailVo();
        detailVo.setPost(postVo);
        detailVo.setAnswers(answerVos);
        return detailVo;
    }

    /**
     * @MethodName vote
     * @Param type
     * @Param targetId
     * @Param direction
     * @Description 投票
     * @Return @return {@link DiscussionVoteVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiscussionVoteVo vote(String type, Long targetId, String direction) {
        if (targetId == null || targetId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 必须大于 0");
        }
        String targetType = DiscussionTargetTypeConstant.normalize(type);
        String normalizedDirection = DiscussionVoteDirectionConstant.normalize(direction);

        if (DiscussionTargetTypeConstant.POST.equals(targetType)) {
            queryNormalDiscussion(targetId);
        } else {
            queryNormalAnswer(targetId);
        }

        String uid = StpUtil.getLoginIdAsString();
        DiscussionLike existsLike = discussionLikeMapper.selectOne(new LambdaQueryWrapper<DiscussionLike>()
                .eq(DiscussionLike::getUid, uid)
                .eq(DiscussionLike::getTargetId, targetId)
                .eq(DiscussionLike::getTargetType, targetType)
                .last("limit 1"));

        int likeDelta = 0;
        if (existsLike == null) {
            DiscussionLike insert = new DiscussionLike();
            insert.setUid(uid);
            insert.setTargetId(targetId);
            insert.setTargetType(targetType);
            insert.setDirection(normalizedDirection);
            discussionLikeMapper.insert(insert);
            if (DiscussionVoteDirectionConstant.UP.equals(normalizedDirection)) {
                likeDelta = 1;
            }
        } else if (!normalizedDirection.equals(existsLike.getDirection())) {
            if (DiscussionVoteDirectionConstant.UP.equals(existsLike.getDirection())
                    && DiscussionVoteDirectionConstant.DOWN.equals(normalizedDirection)) {
                likeDelta = -1;
            } else if (DiscussionVoteDirectionConstant.DOWN.equals(existsLike.getDirection())
                    && DiscussionVoteDirectionConstant.UP.equals(normalizedDirection)) {
                likeDelta = 1;
            }
            existsLike.setDirection(normalizedDirection);
            discussionLikeMapper.updateById(existsLike);
        }

        if (likeDelta != 0) {
            updateLikeNum(targetType, targetId, likeDelta);
        }

        DiscussionVoteVo voteVo = new DiscussionVoteVo();
        voteVo.setTargetType(targetType);
        voteVo.setTargetId(targetId);
        voteVo.setDirection(normalizedDirection);
        voteVo.setLikeNum(currentLikeNum(targetType, targetId));
        return voteVo;
    }

    /**
     * @MethodName createDiscussion
     * @Param request
     * @Description 创建讨论
     * @Return @return {@link DiscussionCreateVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiscussionCreateVo createDiscussion(@NonNull CreateDiscussionRequest request) {
        String title = requireTrimmed(request.getTitle(), "title 不能为空");
        String category = DiscussionCategoryConstant.normalize(request.getCategory());
        String content = requireTrimmed(request.getContent(), "content 不能为空");
        String problemCode = trimToNull(request.getProblemCode());

        if (DiscussionCategoryConstant.PROBLEM.equals(category)) {
            if (problemCode == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "category=Problem 时 problemCode 必填");
            }
            ensureProblemExists(problemCode);
        } else {
            problemCode = null;
        }
        if (Boolean.TRUE.equals(request.getIsTop())) {
            throw new BizException(ResultCode.FORBIDDEN, "外部讨论接口不支持置顶，请使用管理端接口");
        }

        String uid = StpUtil.getLoginIdAsString();
        Discussion discussion = new Discussion();
        discussion.setTitle(title);
        discussion.setContent(content);
        discussion.setDescription(buildDescription(content));
        discussion.setUid(uid);
        discussion.setAuthor(uid);
        discussion.setRole(DiscussionRoleConstant.USER);
        discussion.setCategory(category);
        discussion.setProblemCode(problemCode);
        discussion.setViewNum(0);
        discussion.setLikeNum(0);
        discussion.setTopPriority(DiscussionTopPriorityConstant.NORMAL);
        discussion.setStatus(DiscussionStatusConstant.NORMAL);
        discussionMapper.insert(discussion);
        return new DiscussionCreateVo(discussion.getId());
    }

    /**
     * @MethodName createAnswer
     * @Param discussionId
     * @Param request
     * @Description 创建答案
     * @Return @return {@link DiscussionCreateVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiscussionCreateVo createAnswer(Long discussionId, CreateDiscussionAnswerRequest request) {
        Discussion discussion = queryDiscussionById(discussionId);
        if (discussion.getStatus() != null && discussion.getStatus() == DiscussionStatusConstant.CLOSED) {
            throw new BizException(ResultCode.FORBIDDEN, "该讨论已关闭，无法回复");
        }
        if (discussion.getStatus() != null && discussion.getStatus() != DiscussionStatusConstant.NORMAL) {
            throw new BizException(ResultCode.NOT_FOUND, "讨论不存在");
        }

        String content = requireTrimmed(request.getContent(), "content 不能为空");
        String uid = StpUtil.getLoginIdAsString();

        DiscussionAnswer answer = new DiscussionAnswer();
        answer.setDid(discussion.getId());
        answer.setContent(content);
        answer.setUid(uid);
        answer.setAuthor(uid);
        answer.setLikeNum(0);
        answer.setStatus(DiscussionStatusConstant.NORMAL);
        discussionAnswerMapper.insert(answer);
        return new DiscussionCreateVo(answer.getId());
    }

    /**
     * @MethodName createComment
     * @Param answerId
     * @Param request
     * @Description 创建评论
     * @Return @return {@link DiscussionCreateVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiscussionCreateVo createComment(Long answerId, @NonNull CreateDiscussionCommentRequest request) {
        DiscussionAnswer answer = queryNormalAnswer(answerId);
        String content = requireTrimmed(request.getContent(), "content 不能为空");
        String uid = StpUtil.getLoginIdAsString();

        DiscussionComment comment = new DiscussionComment();
        comment.setAid(answer.getId());
        comment.setContent(content);
        comment.setUid(uid);
        comment.setAuthor(uid);
        comment.setReplyToUid(trimToNull(request.getReplyToUid()));
        comment.setReplyToName(trimToNull(request.getReplyToName()));
        discussionCommentMapper.insert(comment);
        return new DiscussionCreateVo(comment.getId());
    }

    /**
     * @MethodName listRelatedDiscussions
     * @Param problemCode
     * @Param limit
     * @Description 相关讨论列表
     * @Return @return {@link List }<{@link DiscussionRelatedVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @Override
    public List<DiscussionRelatedVo> listRelatedDiscussions(String problemCode, Integer limit) {
        String normalizedCode = requireTrimmed(problemCode, "problemCode 不能为空");
        int queryLimit = limit == null ? 5 : limit;
        if (queryLimit <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "pageSize 必须大于 0");
        }
        if (queryLimit > 20) {
            queryLimit = 20;
        }

        List<Discussion> list = discussionMapper.selectList(new LambdaQueryWrapper<Discussion>()
                .select(Discussion::getId, Discussion::getTitle, Discussion::getTopPriority, Discussion::getGmtCreate)
                .eq(Discussion::getStatus, DiscussionStatusConstant.NORMAL)
                .eq(Discussion::getCategory, DiscussionCategoryConstant.PROBLEM)
                .eq(Discussion::getProblemCode, normalizedCode)
                .orderByDesc(Discussion::getTopPriority)
                .orderByDesc(Discussion::getGmtCreate)
                .last("limit " + queryLimit));

        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(discussion -> {
            DiscussionRelatedVo vo = new DiscussionRelatedVo();
            vo.setId(discussion.getId());
            vo.setTitle(discussion.getTitle());
            return vo;
        }).toList();
    }

    /**
     * @MethodName listAdminDiscussions
     * @Param page
     * @Param pageSize
     * @Param keyword
     * @Param category
     * @Param status
     * @Description 管理端分页查询讨论列表
     * @Return @return {@link PageVo }<{@link AdminDiscussionListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    public PageVo<AdminDiscussionListVo> listAdminDiscussions(int page, int pageSize, String keyword, String category, Integer status) {
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 和 pageSize 必须大于 0");
        }
        String normalizedKeyword = trimToNull(keyword);
        String normalizedCategory = category == null ? null : DiscussionCategoryConstant.normalizeWithAll(category);
        Integer normalizedStatus = status == null ? null : DiscussionStatusConstant.normalize(status);

        LambdaQueryWrapper<Discussion> wrapper = new LambdaQueryWrapper<Discussion>()
                .orderByDesc(Discussion::getTopPriority)
                .orderByDesc(Discussion::getGmtCreate)
                .orderByDesc(Discussion::getId);
        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(Discussion::getTitle, normalizedKeyword)
                    .or()
                    .like(Discussion::getDescription, normalizedKeyword)
                    .or()
                    .like(Discussion::getContent, normalizedKeyword));
        }
        if (normalizedCategory != null && !DiscussionCategoryConstant.ALL.equals(normalizedCategory)) {
            wrapper.eq(Discussion::getCategory, normalizedCategory);
        }
        if (normalizedStatus != null) {
            wrapper.eq(Discussion::getStatus, normalizedStatus);
        }

        Page<Discussion> pageParam = new Page<>(page, pageSize);
        Page<Discussion> pageResult = discussionMapper.selectPage(pageParam, wrapper);
        List<Discussion> records = pageResult.getRecords();
        if (records == null || records.isEmpty()) {
            return new PageVo<>(Collections.emptyList(), pageResult.getTotal());
        }

        Map<Long, Long> answerCountMap = queryAnswerCountMap(records);
        List<AdminDiscussionListVo> list = records.stream()
                .map(post -> toAdminDiscussionListVo(post, answerCountMap.getOrDefault(post.getId(), 0L)))
                .toList();
        return new PageVo<>(list, pageResult.getTotal());
    }

    /**
     * @MethodName updateDiscussionByAdmin
     * @Param discussionId
     * @Param request
     * @Description 管理端编辑讨论
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDiscussionByAdmin(Long discussionId, @NonNull AdminUpdateDiscussionRequest request) {
        Discussion existsDiscussion = queryDiscussionById(discussionId);
        String title = requireTrimmed(request.getTitle(), "title 不能为空");
        String category = DiscussionCategoryConstant.normalize(request.getCategory());
        String content = requireTrimmed(request.getContent(), "content 不能为空");
        int status = DiscussionStatusConstant.normalize(request.getStatus());
        String problemCode = trimToNull(request.getProblemCode());
        if (DiscussionCategoryConstant.PROBLEM.equals(category)) {
            String finalProblemCode = problemCode == null ? trimToNull(existsDiscussion.getProblemCode()) : problemCode;
            if (finalProblemCode == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "category=Problem 时 problemCode 必填");
            }
            ensureProblemExists(finalProblemCode);
            problemCode = finalProblemCode;
        } else {
            problemCode = null;
        }

        Discussion update = new Discussion();
        update.setId(existsDiscussion.getId());
        update.setTitle(title);
        update.setCategory(category);
        update.setProblemCode(problemCode);
        update.setContent(content);
        update.setDescription(buildDescription(content));
        update.setStatus(status);
        update.setTopPriority(DiscussionTopPriorityConstant.fromBoolean(request.getIsTop()));
        discussionMapper.updateById(update);
    }

    /**
     * @MethodName deleteDiscussionByAdmin
     * @Param discussionId
     * @Description 管理端删除讨论
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDiscussionByAdmin(Long discussionId) {
        Discussion discussion = queryDiscussionById(discussionId);

        List<DiscussionAnswer> answerList = discussionAnswerMapper.selectList(new LambdaQueryWrapper<DiscussionAnswer>()
                .select(DiscussionAnswer::getId)
                .eq(DiscussionAnswer::getDid, discussion.getId()));
        List<Long> answerIds = answerList == null
                ? Collections.emptyList()
                : answerList.stream().map(DiscussionAnswer::getId).filter(Objects::nonNull).toList();

        if (!answerIds.isEmpty()) {
            discussionCommentMapper.delete(new LambdaQueryWrapper<DiscussionComment>()
                    .in(DiscussionComment::getAid, answerIds));
            discussionLikeMapper.delete(new LambdaQueryWrapper<DiscussionLike>()
                    .in(DiscussionLike::getTargetId, answerIds)
                    .eq(DiscussionLike::getTargetType, DiscussionTargetTypeConstant.ANSWER));
            discussionAnswerMapper.delete(new LambdaQueryWrapper<DiscussionAnswer>()
                    .in(DiscussionAnswer::getId, answerIds));
        }

        discussionLikeMapper.delete(new LambdaQueryWrapper<DiscussionLike>()
                .eq(DiscussionLike::getTargetId, discussion.getId())
                .eq(DiscussionLike::getTargetType, DiscussionTargetTypeConstant.POST));
        discussionMapper.deleteById(discussion.getId());
    }

    /**
     * @MethodName queryNormalDiscussion
     * @Param discussionId
     * @Description 查询正常状态讨论
     * @Return @return {@link Discussion }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @NotNull
    @Contract("null -> fail")
    private Discussion queryNormalDiscussion(Long discussionId) {
        return queryDiscussionById(discussionId, true);
    }

    /**
     * @MethodName queryDiscussionById
     * @Param discussionId
     * @Description 按 id 查询讨论
     * @Return @return {@link Discussion }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @NotNull
    private Discussion queryDiscussionById(Long discussionId) {
        return queryDiscussionById(discussionId, false);
    }

    /**
     * @MethodName queryDiscussionById
     * @Param discussionId
     * @Param onlyNormal
     * @Description 按 id 查询讨论（可限制仅正常状态）
     * @Return @return {@link Discussion }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @NotNull
    private Discussion queryDiscussionById(Long discussionId, boolean onlyNormal) {
        if (discussionId == null || discussionId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "discussionId 不合法");
        }
        LambdaQueryWrapper<Discussion> wrapper = new LambdaQueryWrapper<Discussion>()
                .eq(Discussion::getId, discussionId)
                .last("limit 1");
        if (onlyNormal) {
            wrapper.eq(Discussion::getStatus, DiscussionStatusConstant.NORMAL);
        }
        Discussion discussion = discussionMapper.selectOne(wrapper);
        if (discussion == null) {
            throw new BizException(ResultCode.NOT_FOUND, "讨论不存在");
        }
        return discussion;
    }

    /**
     * @MethodName queryNormalAnswer
     * @Param answerId
     * @Description 查询正常状态答案
     * @Return @return {@link DiscussionAnswer }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @NotNull
    @Contract("null -> fail")
    private DiscussionAnswer queryNormalAnswer(Long answerId) {
        return queryAnswerById(answerId, true);
    }

    /**
     * @MethodName queryAnswerById
     * @Param answerId
     * @Param onlyNormal
     * @Description 按 id 查询回答（可限制仅正常状态）
     * @Return @return {@link DiscussionAnswer }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @NotNull
    private DiscussionAnswer queryAnswerById(Long answerId, boolean onlyNormal) {
        if (answerId == null || answerId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "answerId 不合法");
        }
        LambdaQueryWrapper<DiscussionAnswer> wrapper = new LambdaQueryWrapper<DiscussionAnswer>()
                .eq(DiscussionAnswer::getId, answerId)
                .last("limit 1");
        if (onlyNormal) {
            wrapper.eq(DiscussionAnswer::getStatus, DiscussionStatusConstant.NORMAL);
        }
        DiscussionAnswer answer = discussionAnswerMapper.selectOne(wrapper);
        if (answer == null) {
            throw new BizException(ResultCode.NOT_FOUND, "回答不存在");
        }
        return answer;
    }

    /**
     * @MethodName queryAnswerCountMap
     * @Param posts
     * @Description 查询答案数量
     * @Return @return {@link Map }<{@link Long }, {@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    private Map<Long, Long> queryAnswerCountMap(@NotNull List<Discussion> posts) {
        List<Long> discussionIds = posts.stream().map(Discussion::getId).toList();
        List<DiscussionAnswer> answers = discussionAnswerMapper.selectList(new LambdaQueryWrapper<DiscussionAnswer>()
                .select(DiscussionAnswer::getDid)
                .in(DiscussionAnswer::getDid, discussionIds)
                .eq(DiscussionAnswer::getStatus, DiscussionStatusConstant.NORMAL));
        if (answers == null || answers.isEmpty()) {
            return Collections.emptyMap();
        }
        return answers.stream().collect(Collectors.groupingBy(DiscussionAnswer::getDid, Collectors.counting()));
    }

    /**
     * @MethodName toDiscussionListVo
     * @Param post
     * @Param answerCount
     * @Description 讨论列表展示对象转换
     * @Return @return {@link DiscussionListVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @NotNull
    private DiscussionListVo toDiscussionListVo(Discussion post, Long answerCount) {
        DiscussionPostVo postVo = toDiscussionPostVo(post);
        DiscussionListVo vo = new DiscussionListVo();
        BeanUtils.copyProperties(postVo, vo);
        vo.setAnswerCount(Math.toIntExact(answerCount == null ? 0L : answerCount));
        return vo;
    }

    /**
     * @MethodName toAdminDiscussionListVo
     * @Param post
     * @Param answerCount
     * @Description 管理端讨论列表展示对象转换
     * @Return @return {@link AdminDiscussionListVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @NotNull
    private AdminDiscussionListVo toAdminDiscussionListVo(Discussion post, Long answerCount) {
        AdminDiscussionListVo vo = new AdminDiscussionListVo();
        vo.setId(post.getId());
        vo.setTitle(post.getTitle());
        vo.setUid(post.getUid());
        vo.setAuthor(post.getAuthor());
        vo.setCategory(post.getCategory());
        vo.setProblemCode(post.getProblemCode());
        vo.setStatus(post.getStatus());
        vo.setTopPriority(post.getTopPriority());
        vo.setViewNum(post.getViewNum());
        vo.setLikeNum(post.getLikeNum());
        vo.setAnswerCount(Math.toIntExact(answerCount == null ? 0L : answerCount));
        vo.setGmtCreate(post.getGmtCreate());
        vo.setGmtModified(post.getGmtModified());
        return vo;
    }

    /**
     * @MethodName toDiscussionPostVo
     * @Param post
     * @Description 讨论主贴展示对象转换
     * @Return @return {@link DiscussionPostVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @NotNull
    private DiscussionPostVo toDiscussionPostVo(@NotNull Discussion post) {
        DiscussionPostVo postVo = new DiscussionPostVo();
        postVo.setId(post.getId());
        postVo.setTitle(post.getTitle());
        postVo.setContent(post.getContent());
        postVo.setDescription(post.getDescription());
        postVo.setUid(post.getUid());
        postVo.setAuthor(post.getAuthor());
        postVo.setRole(post.getRole());
        postVo.setCategory(post.getCategory());
        postVo.setProblemCode(post.getProblemCode());
        postVo.setViewNum(post.getViewNum());
        postVo.setLikeNum(post.getLikeNum());
        postVo.setTopPriority(post.getTopPriority());
        postVo.setGmtCreate(post.getGmtCreate());
        postVo.setGmtModified(post.getGmtModified());
        return postVo;
    }

    /**
     * @MethodName queryCommentMap
     * @Param answers
     * @Description 查询评论列表
     * @Return @return {@link Map }<{@link Long }, {@link List }<{@link DiscussionCommentVo }>>
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @NotNull
    private Map<Long, List<DiscussionCommentVo>> queryCommentMap(List<DiscussionAnswer> answers) {
        if (answers == null || answers.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> answerIds = answers.stream().map(DiscussionAnswer::getId).toList();
        List<DiscussionComment> comments = discussionCommentMapper.selectList(new LambdaQueryWrapper<DiscussionComment>()
                .in(DiscussionComment::getAid, answerIds)
                .orderByAsc(DiscussionComment::getGmtCreate)
                .orderByAsc(DiscussionComment::getId));
        if (comments == null || comments.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<DiscussionCommentVo>> result = new LinkedHashMap<>();
        for (DiscussionComment comment : comments) {
            DiscussionCommentVo vo = new DiscussionCommentVo();
            vo.setId(comment.getId());
            vo.setContent(comment.getContent());
            vo.setUid(comment.getUid());
            vo.setAuthor(comment.getAuthor());
            vo.setReplyToUid(comment.getReplyToUid());
            vo.setReplyToName(comment.getReplyToName());
            vo.setGmtCreate(comment.getGmtCreate());
            result.computeIfAbsent(comment.getAid(), key -> new ArrayList<>()).add(vo);
        }
        return result;
    }

    /**
     * @MethodName updateLikeNum
     * @Param targetType
     * @Param targetId
     * @Param delta
     * @Description 更新点赞数量
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    private void updateLikeNum(String targetType, Long targetId, int delta) {
        if (DiscussionTargetTypeConstant.POST.equals(targetType)) {
            Discussion discussion = queryNormalDiscussion(targetId);
            Discussion update = new Discussion();
            update.setId(discussion.getId());
            int likeNum = discussion.getLikeNum() == null ? 0 : discussion.getLikeNum();
            update.setLikeNum(Math.max(0, likeNum + delta));
            discussionMapper.updateById(update);
        } else {
            DiscussionAnswer answer = queryNormalAnswer(targetId);
            DiscussionAnswer update = new DiscussionAnswer();
            update.setId(answer.getId());
            int likeNum = answer.getLikeNum() == null ? 0 : answer.getLikeNum();
            update.setLikeNum(Math.max(0, likeNum + delta));
            discussionAnswerMapper.updateById(update);
        }
    }

    /**
     * @MethodName currentLikeNum
     * @Param targetType
     * @Param targetId
      * @Description 当前喜欢数量
     * @Return @return {@link Integer }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    private Integer currentLikeNum(String targetType, Long targetId) {
        if (DiscussionTargetTypeConstant.POST.equals(targetType)) {
            Discussion discussion = queryNormalDiscussion(targetId);
            return discussion.getLikeNum() == null ? 0 : discussion.getLikeNum();
        }
        DiscussionAnswer answer = queryNormalAnswer(targetId);
        return answer.getLikeNum() == null ? 0 : answer.getLikeNum();
    }

    /**
     * @MethodName ensureProblemExists
     * @Param problemCode
     * @Description 确保题目存在
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    private void ensureProblemExists(String problemCode) {
        try {
            Result<?> result = problemInternalFeignClient.getProblemBasic(problemCode);
            if (result == null || result.getCode() != ResultCode.SUCCESS || result.getData() == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "题目不存在");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Validate problem failed, problemCode: {}", problemCode, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "题目服务不可用，请稍后重试");
        }
    }

    /**
     * @MethodName buildDescription
     * @Param content
     * @Description 构建描述
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @Nullable
    private String buildDescription(String content) {
        String normalized = trimToNull(content);
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= DESCRIPTION_MAX_LENGTH
                ? normalized
                : normalized.substring(0, DESCRIPTION_MAX_LENGTH);
    }

    /**
     * @MethodName requireTrimmed
     * @Param value
     * @Param message
     * @Description 对输入字符串进行修剪（去除首尾空格），若结果为空则抛出业务异常
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @NotNull
    private String requireTrimmed(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BizException(ResultCode.BAD_REQUEST, message);
        }
        return trimmed;
    }

    /**
     * @MethodName trimToNull
     * @Param value
     * @Description 对输入字符串进行修剪（去除首尾空格），若结果为空则返回 null，否则返回修剪后的字符串
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
