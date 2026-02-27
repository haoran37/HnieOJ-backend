package com.hnieacm.contest.service.manager;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.contest.feign.UserInternalFeignClient;
import com.hnieacm.contest.feign.dto.UserBasicDto;
import com.hnieacm.contest.feign.dto.UserBatchQueryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 用户远程查询管理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContestUserManager {

    private final UserInternalFeignClient userInternalFeignClient;

    /**
     * 批量查询 uid -> username 映射
     */
    public Map<String, String> queryUsernameMap(List<String> uids) {
        List<String> normalizedUids = normalizeUids(uids);
        if (normalizedUids.isEmpty()) {
            return Collections.emptyMap();
        }

        UserBatchQueryRequest request = new UserBatchQueryRequest();
        request.setUids(normalizedUids);
        Result<List<UserBasicDto>> result;
        try {
            result = userInternalFeignClient.queryUsersByUids(request);
        } catch (Exception e) {
            log.error("Query user basic info failed, uids: {}", normalizedUids, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "查询用户信息失败，请稍后重试");
        }

        if (result == null || result.getCode() != ResultCode.SUCCESS) {
            log.warn("Query user basic info returned abnormal result, result: {}", result);
            throw new BizException(ResultCode.INTERNAL_ERROR, "查询用户信息失败，请稍后重试");
        }

        List<UserBasicDto> users = result.getData();
        if (users == null || users.isEmpty()) {
            return Collections.emptyMap();
        }

        return users.stream()
                .filter(Objects::nonNull)
                .filter(user -> user.getUid() != null && !user.getUid().isBlank())
                .collect(Collectors.toMap(UserBasicDto::getUid,
                        user -> user.getUsername() == null ? user.getUid() : user.getUsername(),
                        (oldValue, newValue) -> oldValue));
    }

    /**
     * 标准化 uid
     */
    public List<String> normalizeUids(List<String> uids) {
        if (uids == null || uids.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> uidSet = new LinkedHashSet<>();
        for (String uid : uids) {
            if (uid == null) {
                continue;
            }
            String normalized = uid.trim();
            if (!normalized.isEmpty()) {
                uidSet.add(normalized);
            }
        }
        return uidSet.stream().toList();
    }

    /**
     * 校验 uid 都存在，不存在时抛业务异常
     */
    public void ensureUsersExist(List<String> uids) {
        List<String> normalizedUids = normalizeUids(uids);
        if (normalizedUids.isEmpty()) {
            return;
        }
        Map<String, String> userMap = queryUsernameMap(normalizedUids);
        Set<String> existedUidSet = userMap.keySet();
        for (String uid : normalizedUids) {
            if (!existedUidSet.contains(uid)) {
                throw new BizException(ResultCode.USER_NOT_FOUND, "用户不存在: " + uid);
            }
        }
    }

    /**
     * 批量补齐用户名（不存在时回退 uid）
     */
    public <T> void fillUserName(List<T> records,
                                 Function<T, String> uidExtractor,
                                 java.util.function.BiConsumer<T, String> usernameSetter) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<String> uids = records.stream()
                .map(uidExtractor)
                .toList();
        Map<String, String> userMap = queryUsernameMap(uids);
        for (T record : records) {
            String uid = uidExtractor.apply(record);
            usernameSetter.accept(record, userMap.getOrDefault(uid, uid));
        }
    }
}
