package com.hnieacm.user.service;

import com.hnieacm.user.dto.UpdateUserIpRestrictionRequest;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户 IP 限制服务
 */
public interface UserIpRestrictionService {

    void update(String uid, UpdateUserIpRestrictionRequest request);
}
