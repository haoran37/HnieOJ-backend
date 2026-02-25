package com.hnieacm.announcement.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/24
 * @Description: 公告详情展示对象
 */
@Data
public class AnnouncementDetailVo {

    private Long id;

    private String title;

    private String content;

    private String uid;

    private Integer status;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
