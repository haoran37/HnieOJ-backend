package com.hnieacm.admin.constant;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/24
 * @Description: 公告状态常量
 */
public final class AnnouncementStatusConstant {

    private AnnouncementStatusConstant() {
    }

    public static final int OFFLINE = 0;
    public static final int ONLINE = 1;

    public static boolean isOnline(Integer status) {
        return status == null || status != ONLINE;
    }

    public static boolean isOffline(Integer status) {
        return status != null && status == OFFLINE;
    }
}
