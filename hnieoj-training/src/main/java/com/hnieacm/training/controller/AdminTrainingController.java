package com.hnieacm.training.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.training.dto.AdminTrainingSaveRequest;
import com.hnieacm.training.dto.AdminTrainingStatusRequest;
import com.hnieacm.training.service.TrainingAdminService;
import com.hnieacm.training.vo.AdminTrainingListVo;
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
 * @Description: 题单管理接口（管理员）
 */
@Tag(name = "题单管理模块(管理员)")
@Validated
@RestController
@RequestMapping("/api/admin/training")
@RequiredArgsConstructor
@SaCheckRole(value = {RoleConstant.ADMIN, RoleConstant.ROOT}, mode = SaMode.OR)
public class AdminTrainingController {

    private final TrainingAdminService trainingAdminService;

    @Operation(summary = "获取题单列表(Admin)")
    @GetMapping("/list")
    public Result<PageVo<AdminTrainingListVo>> list(@RequestParam @Min(value = 1, message = "page 必须大于等于 1") int page,
                                                     @RequestParam("pageSize") @Min(value = 1, message = "pageSize 必须大于等于 1") int pageSize,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String type,
                                                     @RequestParam(required = false) String auth,
                                                     @RequestParam(required = false) Boolean status) {
        return Result.success(trainingAdminService.listTrainings(page, pageSize, keyword, type, auth, status));
    }

    @Operation(summary = "添加题单")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody AdminTrainingSaveRequest request) {
        trainingAdminService.createTraining(request);
        return Result.success("创建成功", null);
    }

    @Operation(summary = "编辑题单")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long trainingId,
                               @Valid @RequestBody AdminTrainingSaveRequest request) {
        trainingAdminService.updateTraining(trainingId, request);
        return Result.success("更新成功", null);
    }

    @Operation(summary = "删除题单")
    @DeleteMapping
    public Result<Void> delete(@RequestParam("id") @Min(value = 1, message = "id 必须大于等于 1") Long trainingId) {
        trainingAdminService.deleteTraining(trainingId);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "切换题单状态")
    @PutMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody AdminTrainingStatusRequest request) {
        trainingAdminService.changeTrainingStatus(request);
        return Result.success("状态更新成功", null);
    }

}
