package com.hnieacm.discussion.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.discussion.dto.AdminUpdateDiscussionRequest;
import com.hnieacm.discussion.dto.CreateDiscussionAnswerRequest;
import com.hnieacm.discussion.dto.CreateDiscussionCommentRequest;
import com.hnieacm.discussion.dto.CreateDiscussionRequest;
import com.hnieacm.discussion.vo.AdminDiscussionListVo;
import com.hnieacm.discussion.vo.DiscussionCreateVo;
import com.hnieacm.discussion.vo.DiscussionDetailVo;
import com.hnieacm.discussion.vo.DiscussionListVo;
import com.hnieacm.discussion.vo.DiscussionRelatedVo;
import com.hnieacm.discussion.vo.DiscussionVoteVo;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论服务
 */
public interface DiscussionService {

    PageVo<DiscussionListVo> listDiscussions(int page, int pageSize, String category, String keyword, String sort);

    DiscussionDetailVo getDiscussionDetail(Long discussionId);

    DiscussionVoteVo vote(String type, Long targetId, String direction);

    DiscussionCreateVo createDiscussion(CreateDiscussionRequest request);

    DiscussionCreateVo createAnswer(Long discussionId, CreateDiscussionAnswerRequest request);

    DiscussionCreateVo createComment(Long answerId, CreateDiscussionCommentRequest request);

    List<DiscussionRelatedVo> listRelatedDiscussions(String problemCode, Integer limit);

    PageVo<AdminDiscussionListVo> listAdminDiscussions(int page, int pageSize, String keyword, String category, Integer status);

    void updateDiscussionByAdmin(Long discussionId, AdminUpdateDiscussionRequest request);

    void deleteDiscussionByAdmin(Long discussionId);
}
