package com.hnieacm.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.service.UserLookupService;
import com.hnieacm.user.vo.UserBasicVo;
import com.hnieacm.user.vo.UserCheckVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 用户查询服务实现
 */
@Service
@RequiredArgsConstructor
public class UserLookupServiceImpl implements UserLookupService {

    private final UserInfoMapper userInfoMapper;

    /**
     * @MethodName checkUser
     * @Param query
     * @Description 检测用户存在性
     * @Return @return {@link UserCheckVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    public UserCheckVo checkUser(String query) {
        String normalizedQuery = StrUtil.trim(query);
        if (StrUtil.isBlank(normalizedQuery)) {
            throw new BizException(ResultCode.BAD_REQUEST, "query 不能为空");
        }

        UserInfo user = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getUid, normalizedQuery)
                .last("limit 1"));
        if (user == null) {
            user = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
                    .eq(UserInfo::getUsername, normalizedQuery)
                    .orderByDesc(UserInfo::getGmtCreate)
                    .last("limit 1"));
        }

        UserCheckVo result = new UserCheckVo();
        if (user == null) {
            result.setExists(false);
            return result;
        }
        result.setExists(true);
        result.setUid(user.getUid());
        result.setUsername(user.getUsername());
        return result;
    }

    /**
     * @MethodName queryUserBasicInfoByUids
     * @Param uids
     * @Description 按 uid 查询用户基本信息
     * @Return @return {@link List }<{@link UserBasicVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    public List<UserBasicVo> queryUserBasicInfoByUids(List<String> uids) {
        List<String> normalizedUids = normalizeUids(uids);
        if (normalizedUids.isEmpty()) {
            return Collections.emptyList();
        }

        List<UserInfo> users = userInfoMapper.selectList(new LambdaQueryWrapper<UserInfo>()
                .select(UserInfo::getUid, UserInfo::getUsername)
                .in(UserInfo::getUid, normalizedUids));
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, UserInfo> userMap = users.stream()
                .filter(Objects::nonNull)
                .filter(user -> StrUtil.isNotBlank(user.getUid()))
                .collect(Collectors.toMap(UserInfo::getUid, Function.identity(), (oldValue, newValue) -> oldValue));

        return normalizedUids.stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .map(this::toBasicVo)
                .toList();
    }

    /**
     * @MethodName toBasicVo
     * @Param userInfo
     * @Description 转换为用户基本信息vo
     * @Return @return {@link UserBasicVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private UserBasicVo toBasicVo(UserInfo userInfo) {
        UserBasicVo vo = new UserBasicVo();
        vo.setUid(userInfo.getUid());
        vo.setUsername(StrUtil.blankToDefault(userInfo.getUsername(), userInfo.getUid()));
        return vo;
    }

    /**
     * @MethodName normalizeUids
     * @Param uids
     * @Description 标准化 uids
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private List<String> normalizeUids(List<String> uids) {
        if (uids == null || uids.isEmpty()) {
            return Collections.emptyList();
        }
        return uids.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }
}
