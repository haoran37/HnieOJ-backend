package com.hnieacm.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.entity.UserFavorite;
import com.hnieacm.user.mapper.UserFavoriteMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * @author HnieOJ contributors
 */
@RestController
@SaCheckLogin
@RequestMapping("/api/user/favorites")
@RequiredArgsConstructor
public class UserFavoriteController {
    private static final Set<String> TYPES = Set.of("problem", "training", "contest", "discussion");
    private final UserFavoriteMapper mapper;

    @GetMapping
    public Result<List<UserFavorite>> list(@RequestParam(required = false) String type) {
        if (type != null) {
            validateType(type);
        }
        LambdaQueryWrapper<UserFavorite> query = new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUid, StpUtil.getLoginIdAsString())
                .orderByDesc(UserFavorite::getId);
        if (type != null) {
            query.eq(UserFavorite::getTargetType, type);
        }
        return Result.success(mapper.selectList(query));
    }

    @GetMapping("/check")
    public Result<Boolean> check(@RequestParam String type, @RequestParam String targetId) {
        validate(type, targetId);
        return Result.success(exists(StpUtil.getLoginIdAsString(), type, targetId));
    }

    @PostMapping
    public Result<Void> add(@RequestBody FavoriteRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "收藏参数不能为空");
        }
        validate(request.type, request.targetId);
        String uid = StpUtil.getLoginIdAsString();
        if (exists(uid, request.type, request.targetId)) {
            return Result.success(null);
        }
        UserFavorite favorite = new UserFavorite();
        favorite.setUid(uid);
        favorite.setTargetType(request.type);
        favorite.setTargetId(request.targetId);
        try { mapper.insert(favorite); }
        catch (DuplicateKeyException ignored) { /* concurrent duplicate add is idempotent */ }
        return Result.success(null);
    }

    @DeleteMapping("/{type}/{targetId}")
    public Result<Void> remove(@PathVariable String type, @PathVariable String targetId) {
        validate(type, targetId);
        mapper.delete(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUid, StpUtil.getLoginIdAsString())
                .eq(UserFavorite::getTargetType, type).eq(UserFavorite::getTargetId, targetId));
        return Result.success(null);
    }

    private boolean exists(String uid, String type, String targetId) {
        return mapper.selectCount(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUid, uid).eq(UserFavorite::getTargetType, type)
                .eq(UserFavorite::getTargetId, targetId)) > 0;
    }

    private void validate(String type, String targetId) {
        validateType(type);
        if (targetId == null || targetId.isBlank() || targetId.length() > 64) {
            throw new BizException(ResultCode.BAD_REQUEST, "收藏参数不合法");
        }
    }

    private void validateType(String type) {
        if (type == null || !TYPES.contains(type)) {
            throw new BizException(ResultCode.BAD_REQUEST, "收藏参数不合法");
        }
    }

    @Data
    public static class FavoriteRequest {
        private String type;
        private String targetId;
    }
}
