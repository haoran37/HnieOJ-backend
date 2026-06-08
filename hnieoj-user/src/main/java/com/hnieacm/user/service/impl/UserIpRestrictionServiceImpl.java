package com.hnieacm.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.dto.UpdateUserIpRestrictionRequest;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.service.UserIpRestrictionService;
import com.hnieacm.user.service.manager.UserInfoManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户 IP 限制服务实现
 */
@Service
@RequiredArgsConstructor
public class UserIpRestrictionServiceImpl implements UserIpRestrictionService {

    private final UserInfoMapper userInfoMapper;
    private final UserInfoManager userInfoManager;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String uid, UpdateUserIpRestrictionRequest request) {
        if (request == null || request.getIpRestricted() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "ipRestricted 不能为空");
        }
        userInfoManager.getUserByUid(uid);
        List<String> whitelist = normalizeWhitelist(request.getIpWhitelist());
        if (Boolean.TRUE.equals(request.getIpRestricted()) && whitelist.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "开启 IP 限制时白名单不能为空");
        }
        userInfoMapper.update(null, new LambdaUpdateWrapper<UserInfo>()
                .eq(UserInfo::getUid, uid)
                .set(UserInfo::getIpRestricted, request.getIpRestricted())
                .set(UserInfo::getIpWhitelist, toJson(whitelist)));
    }

    private List<String> normalizeWhitelist(List<String> whitelist) {
        if (whitelist == null) {
            return List.of();
        }
        return whitelist.stream()
                .map(item -> item == null ? "" : item.trim())
                .filter(item -> !item.isEmpty())
                .peek(this::validateIp)
                .distinct()
                .toList();
    }

    private void validateIp(String ip) {
        if (!isIpv4(ip) && !isIpv6(ip)) {
            throw new BizException(ResultCode.BAD_REQUEST, "IP 地址不合法：" + ip);
        }
    }

    private boolean isIpv4(String ip) {
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            int value = Integer.parseInt(part);
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }

    private boolean isIpv6(String ip) {
        if (!ip.contains(":")) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address instanceof Inet6Address;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private String toJson(List<String> whitelist) {
        try {
            return objectMapper.writeValueAsString(whitelist);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "IP 白名单序列化失败");
        }
    }
}
