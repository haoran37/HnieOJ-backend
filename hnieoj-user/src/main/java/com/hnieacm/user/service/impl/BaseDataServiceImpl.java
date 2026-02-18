package com.hnieacm.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.entity.SysClass;
import com.hnieacm.user.entity.SysClassTa;
import com.hnieacm.user.entity.SysCollege;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.SysClassTaMapper;
import com.hnieacm.user.mapper.SysCollegeMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.service.BaseDataService;
import com.hnieacm.user.vo.ClassTeacherVo;
import com.hnieacm.user.vo.ClassTaVo;
import com.hnieacm.user.vo.GradeVo;
import com.hnieacm.user.vo.IdNameVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/17
 * @Description: 基础数据服务实现
 */
@Service
@RequiredArgsConstructor
public class BaseDataServiceImpl implements BaseDataService {

    private final SysCollegeMapper sysCollegeMapper;
    private final SysClassMapper sysClassMapper;
    private final SysClassTaMapper sysClassTaMapper;
    private final UserInfoMapper userInfoMapper;

    /**
     * @MethodName listColleges
     *
     * @Description 学院列表
     * @Return @return {@link List }<{@link IdNameVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/18
     */
    @Override
    public List<IdNameVo> listColleges() {
        List<SysCollege> list = sysCollegeMapper.selectList(
                new LambdaQueryWrapper<SysCollege>()
                        .select(SysCollege::getId, SysCollege::getName)
                        .orderByAsc(SysCollege::getId)
        );
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(c -> new IdNameVo(c.getId(), c.getName())).toList();
    }

    /**
     * @MethodName listGrades
     * @Param collegeId
     * @Description 年纪列表
     * @Return @return {@link List }<{@link GradeVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/18
     */
    @Override
    public List<GradeVo> listGrades(Long collegeId) {
        if (collegeId == null || collegeId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "collegeId 不合法");
        }
        SysCollege college = sysCollegeMapper.selectById(collegeId);
        if (college == null) {
            throw new BizException(ResultCode.COLLEGE_NOT_FOUND, "学院不存在");
        }

        List<Object> grades = sysClassMapper.selectObjs(
                new LambdaQueryWrapper<SysClass>()
                        .select(SysClass::getGrade)
                        .eq(SysClass::getCollegeId, collegeId)
                        .isNotNull(SysClass::getGrade)
                        .groupBy(SysClass::getGrade)
                        .orderByDesc(SysClass::getGrade)
        );
        if (grades == null || grades.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> gradeList = grades.stream()
                .map(o -> StrUtil.trimToNull(o == null ? null : String.valueOf(o)))
                .filter(StrUtil::isNotBlank)
                .toList();

        if (gradeList.isEmpty()) {
            return Collections.emptyList();
        }

        List<GradeVo> result = new ArrayList<>(gradeList.size());
        int id = 1;
        for (String grade : gradeList) {
            result.add(new GradeVo(id++, grade));
        }
        return result;
    }

    /**
     * @MethodName listClasses
     * @Param collegeId
     * @Param grade
     * @Description 班级列表
     * @Return @return {@link List }<{@link IdNameVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/18
     */
    @Override
    public List<IdNameVo> listClasses(Long collegeId, String grade) {
        if (collegeId == null || collegeId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "collegeId 不合法");
        }
        String normalizedGrade = StrUtil.trimToNull(grade);
        if (normalizedGrade == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "grade 不能为空");
        }

        SysCollege college = sysCollegeMapper.selectById(collegeId);
        if (college == null) {
            throw new BizException(ResultCode.COLLEGE_NOT_FOUND, "学院不存在");
        }

        List<SysClass> list = sysClassMapper.selectList(
                new LambdaQueryWrapper<SysClass>()
                        .select(SysClass::getId, SysClass::getName)
                        .eq(SysClass::getCollegeId, collegeId)
                        .eq(SysClass::getGrade, normalizedGrade)
                        .orderByAsc(SysClass::getName)
                        .orderByAsc(SysClass::getId)
        );
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(c -> new IdNameVo(c.getId(), c.getName())).toList();
    }

    /**
     * @MethodName listTeachers
     * @Param classId
     * @Description 班级负责教师列表
     * @Return @return {@link List }<{@link ClassTeacherVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/18
     */
    @Override
    public List<ClassTeacherVo> listTeachers(Long classId) {
        if (classId == null || classId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "classId 不合法");
        }

        SysClass sysClass = sysClassMapper.selectById(classId);
        if (sysClass == null) {
            throw new BizException(ResultCode.CLASS_NOT_FOUND, "班级不存在");
        }

        String teacherUid = StrUtil.trimToNull(sysClass.getTeacherUid());
        if (teacherUid == null) {
            return Collections.emptyList();
        }

        List<UserInfo> users = userInfoMapper.selectList(
                new LambdaQueryWrapper<UserInfo>()
                        .select(UserInfo::getUid, UserInfo::getRealname, UserInfo::getUsername)
                        .eq(UserInfo::getUid, teacherUid)
        );
        if (users == null || users.isEmpty()) {
            return List.of(new ClassTeacherVo(teacherUid, teacherUid));
        }
        UserInfo teacher = users.get(0);
        return List.of(new ClassTeacherVo(teacherUid, resolveDisplayName(teacherUid, teacher)));
    }

    /**
     * @MethodName listTas
     * @Param classId
     * @Description 班级负责助教列表
     * @Return @return {@link List }<{@link ClassTaVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/18
     */
    @Override
    public List<ClassTaVo> listTas(Long classId) {
        if (classId == null || classId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "classId 不合法");
        }

        SysClass sysClass = sysClassMapper.selectById(classId);
        if (sysClass == null) {
            throw new BizException(ResultCode.CLASS_NOT_FOUND, "班级不存在");
        }

        List<SysClassTa> tas = sysClassTaMapper.selectList(
                new LambdaQueryWrapper<SysClassTa>()
                        .select(SysClassTa::getTaUid)
                        .eq(SysClassTa::getClassId, classId)
                        .orderByAsc(SysClassTa::getId)
        );
        if (tas == null || tas.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> taUids = new LinkedHashSet<>();
        for (SysClassTa ta : tas) {
            String taUid = StrUtil.trimToNull(ta.getTaUid());
            if (taUid != null) {
                taUids.add(taUid);
            }
        }
        if (taUids.isEmpty()) {
            return Collections.emptyList();
        }

        List<UserInfo> users = userInfoMapper.selectList(
                new LambdaQueryWrapper<UserInfo>()
                        .select(UserInfo::getUid, UserInfo::getRealname, UserInfo::getUsername)
                        .in(UserInfo::getUid, taUids)
        );
        Map<String, UserInfo> userMap = users == null ? Map.of() :
                users.stream().collect(Collectors.toMap(UserInfo::getUid, Function.identity(), (a, b) -> a));

        List<ClassTaVo> result = new ArrayList<>(taUids.size());
        for (String uid : taUids) {
            UserInfo user = userMap.get(uid);
            String displayName = resolveDisplayName(uid, user);
            result.add(new ClassTaVo(uid, displayName));
        }
        return result;
    }

    /**
     * @MethodName resolveDisplayName
     * @Param uid
     * @Param user
     * @Description 解析显示名称
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/18
     */
    private String resolveDisplayName(String uid, UserInfo user) {
        if (user == null) {
            return uid;
        }
        if (StrUtil.isNotBlank(user.getRealname())) {
            return user.getRealname().trim();
        }
        if (StrUtil.isNotBlank(user.getUsername())) {
            return user.getUsername().trim();
        }
        return uid;
    }
}
