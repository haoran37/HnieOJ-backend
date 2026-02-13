package com.hnieacm.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/12
 * @Description: 登录响应数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVo {
    
    private String token;
    
    private UserInfoVo userInfo;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfoVo {
        private String uid;
        private String username;
        private List<String> roles;
    }
}
