package com.hnieacm.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/24
 * @Description: 公告列表展示对象
 */
@Data
public class AnnouncementListVo {

    private Long id;

    private String title;

    private String uid;

    private Integer status;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
