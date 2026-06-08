package com.hnieacm.user.service.support;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.constant.RoleIdConstant;
import com.hnieacm.common.constant.UserStatusConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.dto.BatchUidsRequest;
import com.hnieacm.user.properties.UserManageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: User management validation support.
 */
@Component
@RequiredArgsConstructor
public class UserManageValidator {

    private static final String DEFAULT_CREATE_USER_PASSWORD = "HnieOJ@123456";

    private final UserManageProperties userManageProperties;

    public void validatePasswordLength(String password) {
        int minLength = userManageProperties.getPasswordMinLength();
        int maxLength = userManageProperties.getPasswordMaxLength();
        if (password.length() < minLength || password.length() > maxLength) {
            throw new BizException(ResultCode.BAD_REQUEST, "password 长度应在 " + minLength + "-" + maxLength + " 之间");
        }
    }

    public void validateUsernameLength(String username) {
        if (username == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "username 不能为空");
        }
        String name = username.trim();
        int minLength = userManageProperties.getUsernameMinLength();
        int maxLength = userManageProperties.getUsernameMaxLength();
        if (name.length() < minLength || name.length() > maxLength) {
            throw new BizException(ResultCode.INVALID_USERNAME, "用户名长度应在 " + minLength + "-" + maxLength + " 之间");
        }
    }

    public Integer resolveStatus(Integer status) {
        if (status == null) {
            return null;
        }
        if (UserStatusConstant.NORMAL == status || UserStatusConstant.DISABLED == status) {
            return status;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "status 参数不合法");
    }

    public List<String> normalizeUids(BatchUidsRequest request) {
        if (request == null || request.getUids() == null) {
            return List.of();
        }
        return normalizeUids(request.getUids());
    }

    public List<String> normalizeUids(List<String> uids) {
        if (uids == null) {
            return List.of();
        }
        return uids.stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
    }

    public long resolveManageableRoleId(String role) {
        if (role == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "role 不能为空");
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "role 不能为空");
        }
        return switch (normalized) {
            case RoleConstant.ADMIN -> RoleIdConstant.ADMIN;
            case RoleConstant.TEACHER -> RoleIdConstant.TEACHER;
            case RoleConstant.TA -> RoleIdConstant.TA;
            default -> throw new BizException(ResultCode.BAD_REQUEST, "role 参数不合法");
        };
    }

    public String generateDefaultPassword() {
        return DEFAULT_CREATE_USER_PASSWORD;
    }
}
