package com.hnieacm.discussion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.discussion.constant.DiscussionStatusConstant;
import com.hnieacm.discussion.entity.Discussion;
import com.hnieacm.discussion.feign.ProblemInternalFeignClient;
import com.hnieacm.discussion.mapper.DiscussionAnswerMapper;
import com.hnieacm.discussion.mapper.DiscussionCommentMapper;
import com.hnieacm.discussion.mapper.DiscussionLikeMapper;
import com.hnieacm.discussion.mapper.DiscussionMapper;
import com.hnieacm.discussion.vo.AdminDiscussionDetailVo;
import com.hnieacm.discussion.vo.DiscussionDetailVo;
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
 * @Description: 管理端讨论详情回归：关闭/正常讨论都必须返回真实正文与状态，管理读取不得增加浏览量或写库；
 * 不存在返回 NOT_FOUND；公开详情原有的浏览量自增行为保持不变。
 */
class AdminDiscussionServiceImplDetailTest {

    private DiscussionMapper discussionMapper;
    private DiscussionAnswerMapper discussionAnswerMapper;
    private DiscussionServiceImpl service;

    @BeforeEach
    void setUp() {
        discussionMapper = mock(DiscussionMapper.class);
        discussionAnswerMapper = mock(DiscussionAnswerMapper.class);
        service = new DiscussionServiceImpl(
                discussionMapper,
                discussionAnswerMapper,
                mock(DiscussionCommentMapper.class),
                mock(DiscussionLikeMapper.class),
                mock(ProblemInternalFeignClient.class)
        );
    }

    private Discussion closedDiscussion() {
        Discussion discussion = new Discussion();
        discussion.setId(21L);
        discussion.setTitle("closed post");
        discussion.setCategory("Notice");
        discussion.setProblemCode("P1001");
        discussion.setContent("closed real content");
        discussion.setStatus(DiscussionStatusConstant.CLOSED);
        discussion.setTopPriority(1);
        discussion.setViewNum(7);
        return discussion;
    }

    @Test
    void adminDetailReturnsRealContentAndStatusForClosedDiscussionWithoutSideEffects() {
        Discussion discussion = closedDiscussion();
        when(discussionMapper.selectOne(any())).thenReturn(discussion);

        AdminDiscussionDetailVo detail = service.getAdminDiscussionDetail(21L);

        assertThat(detail.getId()).isEqualTo(21L);
        assertThat(detail.getTitle()).isEqualTo("closed post");
        assertThat(detail.getCategory()).isEqualTo("Notice");
        assertThat(detail.getProblemCode()).isEqualTo("P1001");
        assertThat(detail.getContent()).isEqualTo("closed real content");
        assertThat(detail.getStatus()).isEqualTo(DiscussionStatusConstant.CLOSED);
        assertThat(detail.getIsTop()).isTrue();
        // 管理读取不得写库（不增加浏览量、不改动记录）
        verify(discussionMapper, never()).updateById(any(Discussion.class));
    }

    @Test
    void adminDetailMapsTopPriorityZeroToNotTop() {
        Discussion discussion = closedDiscussion();
        discussion.setStatus(DiscussionStatusConstant.NORMAL);
        discussion.setTopPriority(0);
        when(discussionMapper.selectOne(any())).thenReturn(discussion);

        AdminDiscussionDetailVo detail = service.getAdminDiscussionDetail(21L);

        assertThat(detail.getStatus()).isEqualTo(DiscussionStatusConstant.NORMAL);
        assertThat(detail.getIsTop()).isFalse();
    }

    @Test
    void missingDiscussionReturnsNotFoundBusinessCode() {
        when(discussionMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getAdminDiscussionDetail(999999999L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND);
        verify(discussionMapper, never()).updateById(any(Discussion.class));
    }

    @Test
    void publicDetailStillIncrementsViewNum() {
        Discussion discussion = closedDiscussion();
        discussion.setStatus(DiscussionStatusConstant.NORMAL);
        discussion.setViewNum(7);
        when(discussionMapper.selectOne(any())).thenReturn(discussion);

        DiscussionDetailVo detail = service.getDiscussionDetail(21L);

        assertThat(detail.getPost().getViewNum()).isEqualTo(8);
        verify(discussionMapper).updateById(any(Discussion.class));
    }

    @Test
    void serializedAdminDetailExposesOnlyEditableFields() throws Exception {
        AdminDiscussionDetailVo vo = new AdminDiscussionDetailVo();
        vo.setId(21L);
        vo.setTitle("t");
        vo.setCategory("Notice");
        vo.setProblemCode("P1001");
        vo.setContent("body");
        vo.setStatus(DiscussionStatusConstant.CLOSED);
        vo.setIsTop(true);

        JsonNode node = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(vo));

        assertThat(node.size()).isEqualTo(7);
        assertThat(node.get("id").asLong()).isEqualTo(21L);
        assertThat(node.get("title").asText()).isEqualTo("t");
        assertThat(node.get("category").asText()).isEqualTo("Notice");
        assertThat(node.get("problemCode").asText()).isEqualTo("P1001");
        assertThat(node.get("content").asText()).isEqualTo("body");
        assertThat(node.get("status").asInt()).isEqualTo(DiscussionStatusConstant.CLOSED);
        assertThat(node.get("isTop").asBoolean()).isTrue();
        assertThat(node.has("answers")).isFalse();
    }
}
