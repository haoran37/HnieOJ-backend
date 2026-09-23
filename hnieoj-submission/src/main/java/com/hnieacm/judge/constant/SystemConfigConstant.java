package com.hnieacm.judge.constant;

import java.util.Set;
import com.hnieacm.common.constant.RegisterModeConstant;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 系统配置常量
 */
public final class SystemConfigConstant {

    private SystemConfigConstant() {
    }

    public static final Integer DEFAULT_CONFIG_ID = 1;

    public static final Integer ENABLED_STATUS = 1;

    public static final Integer DISABLED_STATUS = 0;

    public static final Integer MIN_SUBMISSION_INTERVAL = 1;

    public static final String SYSTEM_SCHEMA = "hnieoj_system_db";

    public static final String SYS_CONFIG_TABLE_NAME = SYSTEM_SCHEMA + ".sys_config";

    public static final String REGISTER_MODE_OPEN = RegisterModeConstant.OPEN;

    public static final String REGISTER_MODE_EMAIL_SUFFIX = RegisterModeConstant.EMAIL_SUFFIX;

    public static final String REGISTER_MODE_INVITE_CODE = RegisterModeConstant.INVITE_CODE;

    public static final Set<String> REGISTER_MODE_SET = Set.of(
            REGISTER_MODE_OPEN,
            REGISTER_MODE_EMAIL_SUFFIX,
            REGISTER_MODE_INVITE_CODE
    );
}
