package com.hnieacm.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.user.mapper.UserFavoriteMapper;
import com.hnieacm.user.vo.FavoriteTrainingStatVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author HnieOJ contributors
 */
@RestController
@RequestMapping("/api/admin/favorites")
@RequiredArgsConstructor
public class AdminFavoriteStatsController {
    private final UserFavoriteMapper mapper;

    @GetMapping("/top-trainings")
    public Result<List<FavoriteTrainingStatVo>> topTrainings() {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
        return Result.success(mapper.topTrainings());
    }
}
