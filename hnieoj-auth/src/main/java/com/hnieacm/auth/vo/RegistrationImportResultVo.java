package com.hnieacm.auth.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 注册信息导入结果
 */
@Data
public class RegistrationImportResultVo {

    private Integer successCount = 0;

    private Integer failedCount = 0;

    private List<String> successUids = new ArrayList<>();

    private List<FailureItem> failures = new ArrayList<>();

    public void addSuccess(String uid) {
        successCount++;
        successUids.add(uid);
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
    public static class FailureItem {
        private Integer rowNo;

        private String uid;

        private String reason;
    }
}
