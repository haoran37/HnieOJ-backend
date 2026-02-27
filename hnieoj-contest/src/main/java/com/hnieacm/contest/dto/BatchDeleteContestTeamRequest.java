package com.hnieacm.contest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 批量删除比赛队伍请求参数
 */
@Data
public class BatchDeleteContestTeamRequest {

    @NotEmpty(message = "ids 不能为空")
    private List<@NotNull(message = "id 不能为空") @Min(value = 1, message = "id 必须大于 0") Long> ids;
}
