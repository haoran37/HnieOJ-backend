package com.hnieacm.discussion.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论详情展示对象
 */
@Data
public class DiscussionDetailVo {

    private DiscussionPostVo post;

    private List<DiscussionAnswerVo> answers;
}
