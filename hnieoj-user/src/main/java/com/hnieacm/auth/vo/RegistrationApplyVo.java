package com.hnieacm.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 注册申请列表展示对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationApplyVo {

    private String uid;

    private String username;

    private String email;

    private Long collegeId;

    private String collegeName;

    private Long classId;

    private String className;

    private String grade;

    private String qq;

    private Integer status;

    private String replyInfo;

    /**
     * 提交时间（毫秒时间戳）
     */
    private Long submitTime;
}

