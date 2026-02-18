package com.hnieacm.user.service;

import com.hnieacm.user.vo.ClassTeacherVo;
import com.hnieacm.user.vo.ClassTaVo;
import com.hnieacm.user.vo.GradeVo;
import com.hnieacm.user.vo.IdNameVo;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/17
 * @Description: 基础数据服务
 */
public interface BaseDataService {

    /**
     * 获取学院列表
     */
    List<IdNameVo> listColleges();

    /**
     * 获取学院下年级列表
     */
    List<GradeVo> listGrades(Long collegeId);

    /**
     * 获取学院+年级下班级列表
     */
    List<IdNameVo> listClasses(Long collegeId, String grade);

    /**
     * 获取班级老师列表（负责教师）
     */
    List<ClassTeacherVo> listTeachers(Long classId);

    /**
     * 获取班级助教列表
     */
    List<ClassTaVo> listTas(Long classId);
}
