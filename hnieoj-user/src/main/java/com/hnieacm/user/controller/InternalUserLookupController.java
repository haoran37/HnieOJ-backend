package com.hnieacm.user.controller;

import com.hnieacm.common.result.Result;
import com.hnieacm.user.dto.UserBatchQueryRequest;
import com.hnieacm.user.service.UserLookupService;
import com.hnieacm.user.vo.UserBasicVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 用户内部查询接口
 */
@Tag(name = "用户内部查询接口")
@Validated
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserLookupController {

    private final UserLookupService userLookupService;

    @Operation(summary = "批量查询用户基础信息")
    @PostMapping("/basic-info")
    public Result<List<UserBasicVo>> queryUsersByUids(@Valid @RequestBody UserBatchQueryRequest request) {
        return Result.success(userLookupService.queryUserBasicInfoByUids(request.getUids()));
    }
}
