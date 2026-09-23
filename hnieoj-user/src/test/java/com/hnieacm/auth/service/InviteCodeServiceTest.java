package com.hnieacm.auth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hnieacm.auth.entity.InviteCode;
import com.hnieacm.auth.mapper.InviteCodeMapper;
import com.hnieacm.common.exception.BizException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;

/** A one-time code must be consumed by one successful conditional update. */
class InviteCodeServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "invite-code-test"), InviteCode.class);
    }

    @Test
    void rejectsAlreadyConsumedInvitation() {
        InviteCodeMapper mapper = mock(InviteCodeMapper.class);
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1, 0);
        InviteCodeService service = new InviteCodeService(mapper);

        service.consume("sample-code", "first-user");

        ArgumentCaptor<LambdaUpdateWrapper<InviteCode>> query = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("status", "used_uid", "expires_at");
        assertThat(query.getValue().getParamNameValuePairs()).containsValue(1);

        assertThatThrownBy(() -> service.consume("sample-code", "second-user"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已使用");
    }
}
