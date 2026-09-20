package com.hnieacm.user.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 定向通知详情/预览展示对象，包含已有目标设置
 */
@Data
public class UserNoticeDetailVo {

    private Long id;

    private String title;

    private String content;

    private String targetType;

    /**
     * 已保存的目标 ID 集合（USERS 为 uid，CLASSES 为班级 id 字符串）
     */
    private List<String> targetIds;

    private String status;

    private String creatorUid;

    private LocalDateTime publishedAt;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
