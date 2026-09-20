package com.hnieacm.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 用户身份资料（仅实名/学院/年级/班级）展示对象。
 * <p>
 * 同时用于变更申请原值/目标值的 JSON 序列化。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileIdentityVo {

    private String realname;

    private Long collegeId;

    private String grade;

    private Long classId;
}
