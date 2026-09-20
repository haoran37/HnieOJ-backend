package com.hnieacm.discussion.vo;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 管理端讨论完整详情展示对象（关闭/正常状态均可读取真实正文与状态）
 */
@Data
public class AdminDiscussionDetailVo {

    private Long id;

    private String title;

    private String category;

    private String problemCode;

    private String content;

    private Integer status;

    /**
     * 是否置顶：由 topPriority > 0 映射
     */
    private Boolean isTop;
}
