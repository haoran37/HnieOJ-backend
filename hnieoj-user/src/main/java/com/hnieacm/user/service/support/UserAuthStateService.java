package com.hnieacm.user.service.support;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.hnieacm.common.constant.AuthCacheConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.feign.AuthInternalFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: User auth cache and session state support.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAuthStateService {

    private final AuthInternalFeignClient authInternalFeignClient;
    private final StringRedisTemplate stringRedisTemplate;

    public void kickoutUserSafely(String uid) {
        try {
            List<String> tokenValues = StpUtil.getTokenValueListByLoginId(uid);
            if (tokenValues != null && !tokenValues.isEmpty()) {
                for (String tokenValue : tokenValues) {
                    if (StrUtil.isNotBlank(tokenValue)) {
                        StpUtil.logoutByTokenValue(tokenValue.trim());
                    }
                }
            }
            StpUtil.logout(uid);
            StpUtil.kickout(uid);
        } catch (Exception e) {
            log.debug("Kickout ignored, uid: {}, msg: {}", uid, e.getMessage());
        } finally {
            deleteUserAuthCache(uid);
        }
    }

    public void refreshAuthCacheSafely(String uid) {
        try {
            Result<Void> result = authInternalFeignClient.refreshUserAuthCache(uid);
            if (result == null || result.getCode() != ResultCode.SUCCESS) {
                log.warn("Refresh auth cache failed, uid: {}, result: {}", uid, result);
            }
        } catch (Exception e) {
            log.warn("Refresh auth cache failed, uid: {}", uid, e);
        }
    }

    public void deleteUserAuthCache(String uid) {
        if (StrUtil.isBlank(uid)) {
            return;
        }
        try {
            stringRedisTemplate.delete(AuthCacheConstant.ROLE_CACHE_PREFIX + uid);
            stringRedisTemplate.delete(AuthCacheConstant.PERMISSION_CACHE_PREFIX + uid);
        } catch (Exception e) {
            log.debug("Delete auth cache ignored, uid: {}, msg: {}", uid, e.getMessage());
        }
    }
}
