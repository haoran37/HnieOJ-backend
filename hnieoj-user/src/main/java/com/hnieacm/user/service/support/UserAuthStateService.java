package com.hnieacm.user.service.support;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.hnieacm.auth.service.UserAuthCacheService;
import com.hnieacm.common.constant.AuthCacheConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    private final UserAuthCacheService userAuthCacheService;
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

    /**
     * 刷新鉴权缓存：授权/改权类方法运行在事务中，此时角色与权限行尚未提交，
     * 若在事务内直接写 Redis，一旦回滚就会把未生效权限暴露给网关鉴权。
     * 因此事务内只登记 afterCommit 回调，仅提交成功后刷新；回滚不刷新；无事务调用立即刷新。
     */
    public void refreshAuthCacheSafely(String uid) {
        if (StrUtil.isBlank(uid)) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheUserAuthSafely(uid);
                }
            });
            return;
        }
        cacheUserAuthSafely(uid);
    }

    /**
     * 静默刷新鉴权缓存，避免缓存故障影响主业务。
     */
    private void cacheUserAuthSafely(String uid) {
        try {
            userAuthCacheService.cacheUserAuth(uid);
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
