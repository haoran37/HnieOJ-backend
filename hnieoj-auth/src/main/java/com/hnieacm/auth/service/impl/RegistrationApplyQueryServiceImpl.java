package com.hnieacm.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.auth.entity.SysClass;
import com.hnieacm.auth.entity.SysCollege;
import com.hnieacm.auth.entity.UserRegisterApply;
import com.hnieacm.auth.mapper.SysClassMapper;
import com.hnieacm.auth.mapper.SysCollegeMapper;
import com.hnieacm.auth.mapper.UserRegisterApplyMapper;
import com.hnieacm.auth.service.RegistrationApplyQueryService;
import com.hnieacm.auth.vo.RegistrationApplyVo;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 注册申请查询服务实现
 */
@Service
@RequiredArgsConstructor
public class RegistrationApplyQueryServiceImpl implements RegistrationApplyQueryService {

    private final UserRegisterApplyMapper userRegisterApplyMapper;
    private final SysCollegeMapper sysCollegeMapper;
    private final SysClassMapper sysClassMapper;

    /**
     * @MethodName list
     * @Param page
     * @Param pageSize
     * @Param status
     * @Param keyword
     * @Description 注册申请列表
     * @Return @return {@link PageVo }<{@link RegistrationApplyVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    @Override
    public PageVo<RegistrationApplyVo> list(int page, int pageSize, Integer status, String keyword) {
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 和 pageSize 必须大于 0");
        }

        LambdaQueryWrapper<UserRegisterApply> queryWrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            queryWrapper.eq(UserRegisterApply::getStatus, status);
        }

        String key = StrUtil.trimToNull(keyword);
        if (key != null) {
            queryWrapper.and(w -> w.like(UserRegisterApply::getUid, key)
                    .or().like(UserRegisterApply::getUsername, key)
                    .or().like(UserRegisterApply::getEmail, key));
        }

        queryWrapper.orderByDesc(UserRegisterApply::getGmtCreate).orderByDesc(UserRegisterApply::getId);

        Page<UserRegisterApply> mpPage = new Page<>(page, pageSize);
        Page<UserRegisterApply> result = userRegisterApplyMapper.selectPage(mpPage, queryWrapper);
        if (result.getRecords() == null || result.getRecords().isEmpty()) {
            return new PageVo<>(Collections.emptyList(), result.getTotal());
        }

        Set<Long> collegeIds = result.getRecords().stream()
                .map(UserRegisterApply::getCollegeId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Set<Long> classIds = result.getRecords().stream()
                .map(UserRegisterApply::getClassId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        Map<Long, String> collegeNameMap = collegeIds.isEmpty() ? Map.of() :
                sysCollegeMapper.selectBatchIds(collegeIds).stream()
                        .collect(Collectors.toMap(SysCollege::getId, SysCollege::getName, (a, b) -> a));

        Map<Long, String> classNameMap = classIds.isEmpty() ? Map.of() :
                sysClassMapper.selectBatchIds(classIds).stream()
                        .collect(Collectors.toMap(SysClass::getId, SysClass::getName, (a, b) -> a));

        var voList = result.getRecords().stream().map(apply -> new RegistrationApplyVo(
                apply.getUid(),
                apply.getUsername(),
                apply.getEmail(),
                apply.getCollegeId(),
                mapValue(collegeNameMap, apply.getCollegeId()),
                apply.getClassId(),
                mapValue(classNameMap, apply.getClassId()),
                apply.getGrade(),
                apply.getQq(),
                apply.getStatus(),
                apply.getReplyInfo(),
                apply.getGmtCreate() == null ? null : apply.getGmtCreate().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )).toList();

        return new PageVo<>(voList, result.getTotal());
    }

    /**
     * @MethodName mapValue
     * @Param map
     * @Param id
     * @Description 映射值
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    private String mapValue(Map<Long, String> map, Long id) {
        if (id == null) {
            return null;
        }
        return map.get(id);
    }
}

