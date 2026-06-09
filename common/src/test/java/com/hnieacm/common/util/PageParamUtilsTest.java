package com.hnieacm.common.util;

import com.hnieacm.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 分页参数工具测试
 */
class PageParamUtilsTest {

    @Test
    void shouldValidateNormalPageParams() {
        PageParamUtils.validate(1, 100);

        assertThat(PageParamUtils.normalizePage(null)).isEqualTo(1);
        assertThat(PageParamUtils.normalizePageSize(null)).isEqualTo(20);
    }

    @Test
    void shouldRejectInvalidPageParams() {
        assertThatThrownBy(() -> PageParamUtils.validate(0, 10))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> PageParamUtils.validate(1, 101))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> PageParamUtils.normalizePageSize(101))
                .isInstanceOf(BizException.class);
    }
}
