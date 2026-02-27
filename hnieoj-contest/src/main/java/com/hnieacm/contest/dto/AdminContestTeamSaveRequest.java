package com.hnieacm.contest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 新增/编辑比赛队伍请求参数
 */
@Data
public class AdminContestTeamSaveRequest {

    @Min(value = 1, message = "cid 必须大于 0")
    private Long cid;

    @NotBlank(message = "name 不能为空")
    private String name;

    @NotBlank(message = "member1Uid 不能为空")
    private String member1Uid;

    private String member2Uid;

    private String member3Uid;
}
