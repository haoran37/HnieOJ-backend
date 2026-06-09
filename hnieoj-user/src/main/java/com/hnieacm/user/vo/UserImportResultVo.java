package com.hnieacm.user.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户导入结果
 */
@Data
public class UserImportResultVo {

    private Integer successCount = 0;

    private Integer failedCount = 0;

    private List<CreatedUserItem> createdUsers = new ArrayList<>();

    private List<FailureItem> failures = new ArrayList<>();

    public void addSuccess(String uid, String initialPassword) {
        successCount++;
        CreatedUserItem item = new CreatedUserItem();
        item.setUid(uid);
        item.setInitialPassword(initialPassword);
        createdUsers.add(item);
    }

    public void addFailure(Integer rowNo, String uid, String reason) {
        failedCount++;
        FailureItem item = new FailureItem();
        item.setRowNo(rowNo);
        item.setUid(uid);
        item.setReason(reason);
        failures.add(item);
    }

    @Data
    public static class CreatedUserItem {
        private String uid;

        private String initialPassword;
    }

    @Data
    public static class FailureItem {
        private Integer rowNo;

        private String uid;

        private String reason;
    }
}
