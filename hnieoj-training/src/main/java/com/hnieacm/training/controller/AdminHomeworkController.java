package com.hnieacm.training.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.training.dto.AdminHomeworkSaveRequest;
import com.hnieacm.training.dto.AdminHomeworkStatusRequest;
import com.hnieacm.training.service.HomeworkAdminService;
import com.hnieacm.training.vo.AdminHomeworkDetailVo;
import com.hnieacm.training.vo.AdminHomeworkListVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 作业管理接口（管理员）
 */
@Tag(name = "作业管理模块(管理员)")
@Validated
@RestController
@RequestMapping("/api/admin/homework")
@RequiredArgsConstructor
@SaCheckRole(value = {RoleConstant.ADMIN, RoleConstant.ROOT}, mode = SaMode.OR)
public class AdminHomeworkController {

    private final HomeworkAdminService homeworkAdminService;

    @Operation(summary = "获取作业列表(Admin)")
    @GetMapping("/list")
    public Result<PageVo<AdminHomeworkListVo>> list(@RequestParam @Min(value = 1, message = "page 必须大于等于 1") int page,
                                                     @RequestParam("pageSize") @Min(value = 1, message = "pageSize 必须大于等于 1") int pageSize,
                                                     @RequestParam(required = false) String keyword) {
        return Result.success(homeworkAdminService.listHomeworks(page, pageSize, keyword));
    }

    @Operation(summary = "添加作业")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody AdminHomeworkSaveRequest request) {
        homeworkAdminService.createHomework(request);
        return Result.success("创建成功", null);
    }

    @Operation(summary = "编辑作业")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long homeworkId,
                               @Valid @RequestBody AdminHomeworkSaveRequest request) {
        homeworkAdminService.updateHomework(homeworkId, request);
        return Result.success("更新成功", null);
    }

    @Operation(summary = "删除作业")
    @DeleteMapping
    public Result<Void> delete(@RequestParam("id") @Min(value = 1, message = "id 必须大于等于 1") Long homeworkId) {
        homeworkAdminService.deleteHomework(homeworkId);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "切换作业状态")
    @PutMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody AdminHomeworkStatusRequest request) {
        homeworkAdminService.changeHomeworkStatus(request);
        return Result.success("状态更新成功", null);
    }

    @Operation(summary = "获取作业详情(Admin)")
    @GetMapping("/{id}")
    public Result<AdminHomeworkDetailVo> detail(@PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long homeworkId) {
        return Result.success(homeworkAdminService.getHomeworkDetail(homeworkId));
    }
}
