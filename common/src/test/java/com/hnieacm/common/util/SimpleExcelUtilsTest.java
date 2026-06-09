package com.hnieacm.common.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 简单 Excel 工具测试
 */
class SimpleExcelUtilsTest {

    @Test
    void shouldBuildAndReadTemplateRows() {
        List<String> headers = List.of("uid", "username");
        byte[] content = SimpleExcelUtils.buildTemplate("用户", headers, List.of(List.of("20220001", "张三")));

        List<Map<String, String>> rows = SimpleExcelUtils.readRows(new ByteArrayInputStream(content), headers, 10);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("_rowNo")).isEqualTo("2");
        assertThat(rows.get(0).get("uid")).isEqualTo("20220001");
        assertThat(rows.get(0).get("username")).isEqualTo("张三");
    }
}
