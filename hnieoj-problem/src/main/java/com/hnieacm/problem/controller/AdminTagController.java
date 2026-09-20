package com.hnieacm.problem.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.problem.dto.TagCreateRequest;
import com.hnieacm.problem.dto.TagUpdateRequest;
import com.hnieacm.problem.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 标签管理接口：仅 ADMIN/ROOT，并按既有 PROBLEM_CREATE/UPDATE/DELETE 权限检查。
 * <p>hnieoj-problem 未注册 SaInterceptor，注解不会生效，因此每个写操作都显式执行角色+权限校验。</p>
 */
@Tag(name = "标签管理模块（管理员）")
@Validated
@RestController
@RequestMapping("/api/admin/tags")
@RequiredArgsConstructor
public class AdminTagController {

    private final TagService tagService;

    @Operation(summary = "创建标签")
    @SaCheckPermission(PermissionConstant.PROBLEM_CREATE)
    @PostMapping
    public Result<Void> create(@Valid @RequestBody TagCreateRequest request) {
        checkAdminRole();
        StpUtil.checkPermission(PermissionConstant.PROBLEM_CREATE);
        tagService.createTag(request);
        return Result.success("创建成功", null);
    }

    @Operation(summary = "更新标签")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Min(value = 1, message = "id 必须大于 0") Long id,
                               @Valid @RequestBody TagUpdateRequest request) {
        checkAdminRole();
        StpUtil.checkPermission(PermissionConstant.PROBLEM_UPDATE);
        tagService.updateTag(id, request);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除标签")
    @SaCheckPermission(PermissionConstant.PROBLEM_DELETE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(value = 1, message = "id 必须大于 0") Long id) {
        checkAdminRole();
        StpUtil.checkPermission(PermissionConstant.PROBLEM_DELETE);
        tagService.deleteTag(id);
        return Result.success("删除成功", null);
    }

    private void checkAdminRole() {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
    }
}
