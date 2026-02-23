package com.hnieacm.discussion.vo;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 点赞结果展示对象
 */
@Data
public class DiscussionVoteVo {

    private String targetType;

    private Long targetId;

    private String direction;

    private Integer likeNum;
}
