package com.hnieacm.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hnieacm.common.result.Result;
import com.hnieacm.user.service.BaseDataService;
import com.hnieacm.user.vo.ClassTeacherVo;
import com.hnieacm.user.vo.ClassTaVo;
import com.hnieacm.user.vo.GradeVo;
import com.hnieacm.user.vo.IdNameVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/17
 * @Description: 基础数据模块对外接口
 */
@Tag(name = "基础数据模块(学院/班级)")
@Validated
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SaCheckLogin
public class BaseDataController {

    private final BaseDataService baseDataService;

    @Operation(summary = "获取学院列表")
    @GetMapping("/colleges")
    public Result<List<IdNameVo>> listColleges() {
        return Result.success(baseDataService.listColleges());
    }

    @Operation(summary = "获取年级列表")
    @GetMapping("/colleges/{college}/grades")
    public Result<List<GradeVo>> listGrades(@PathVariable("college") @Min(value = 1, message = "college 必须>=1") Long collegeId) {
        return Result.success(baseDataService.listGrades(collegeId));
    }

    @Operation(summary = "获取班级列表")
    @GetMapping("/colleges/{college}/grades/{grade}/classes")
    public Result<List<IdNameVo>> listClasses(@PathVariable("college") @Min(value = 1, message = "college 必须>=1") Long collegeId,
                                              @PathVariable String grade) {
        return Result.success(baseDataService.listClasses(collegeId, grade));
    }

    @Operation(summary = "获取班级老师列表")
    @GetMapping("/classes/{id}/teachers")
    public Result<List<ClassTeacherVo>> listTeachers(@PathVariable("id") @Min(value = 1, message = "id 必须>=1") Long classId) {
        return Result.success(baseDataService.listTeachers(classId));
    }

    @Operation(summary = "获取班级助教列表")
    @GetMapping("/classes/{id}/tas")
    public Result<List<ClassTaVo>> listTas(@PathVariable("id") @Min(value = 1, message = "id 必须>=1") Long classId) {
        return Result.success(baseDataService.listTas(classId));
    }
}
