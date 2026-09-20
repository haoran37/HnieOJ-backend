package com.hnieacm.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 提交身份资料（实名/学院/年级/班级）变更申请请求
 */
@Data
public class ProfileChangeCreateRequest {

    @NotBlank(message = "realname 不能为空")
    @Size(max = 50, message = "realname 长度不能超过 50")
    private String realname;

    @NotNull(message = "collegeId 不能为空")
    private Long collegeId;

    @NotBlank(message = "grade 不能为空")
    @Size(max = 20, message = "grade 长度不能超过 20")
    private String grade;

    @NotNull(message = "classId 不能为空")
    private Long classId;

    @NotBlank(message = "reason 不能为空")
    @Size(max = 1000, message = "reason 长度不能超过 1000")
    private String reason;
}
