package com.hnieacm.user.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 批量操作结果
 */
@Data
public class BatchOperationResultVo {

    private Integer successCount = 0;

    private Integer failedCount = 0;

    private List<FailureItem> failures = new ArrayList<>();

    public void addSuccess() {
        successCount++;
    }

    public void addFailure(String id, String reason) {
        failedCount++;
        FailureItem item = new FailureItem();
        item.setId(id);
        item.setReason(reason);
        failures.add(item);
    }

    @Data
    public static class FailureItem {
        private String id;

        private String reason;
    }
}
