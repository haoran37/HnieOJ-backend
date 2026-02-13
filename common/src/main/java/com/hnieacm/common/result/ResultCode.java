package com.hnieacm.common.result;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/10
 * @Description: 统一响应码定义
 */
public class ResultCode {
    public static final int SUCCESS = 200;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int INTERNAL_ERROR = 500;
    
    // 业务错误码 - 用户相关
    public static final int USER_NOT_FOUND = 1001;
    public static final int USER_ALREADY_EXISTS = 1002;
    public static final int PASSWORD_ERROR = 1003;
    public static final int PERMISSION_DENIED = 1004;
    public static final int USER_DISABLED = 1005;
    public static final int INVALID_USERNAME = 1006;
    public static final int INVALID_EMAIL = 1007;
    public static final int INVALID_QQ = 1008;
    public static final int COLLEGE_NOT_FOUND = 1009;
    public static final int CLASS_NOT_FOUND = 1010;
    public static final int GRADE_NOT_FOUND = 1011;
    
    // 业务错误码 - 题目相关
    public static final int PROBLEM_NOT_FOUND = 2001;
    
    // 业务错误码 - 提交相关
    public static final int SUBMISSION_NOT_FOUND = 3001;
}
