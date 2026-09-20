package com.hnieacm.user.service;

import com.hnieacm.user.dto.UpdatePasswordRequest;
import com.hnieacm.user.dto.UpdateUserProfileRequest;
import com.hnieacm.user.vo.UserProfileVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 用户信息服务
 */
public interface UserProfileService {

    /**
     * 获取指定登录态用户信息。
     *
     * @param uid 登录态 uid，调用方保证来自服务端会话
     */
    UserProfileVo getCurrentUserProfile(String uid);

    /**
     * 本人自助修改普通资料（username/avatar/qq/github/blog 白名单）。
     *
     * @param uid     登录态 uid，调用方保证来自服务端会话
     * @param request 待修改字段；null 表示未传并保留原值，空串表示清除可选项
     */
    void updateCurrentUserProfile(String uid, UpdateUserProfileRequest request);

    /**
     * 本人自助修改密码：校验旧密码、锁定用户行、BCrypt 保存，并在事务提交后失效该用户全部会话。
     *
     * @param uid     登录态 uid，调用方保证来自服务端会话
     * @param request 旧/新密码
     */
    void updatePassword(String uid, UpdatePasswordRequest request);
}
