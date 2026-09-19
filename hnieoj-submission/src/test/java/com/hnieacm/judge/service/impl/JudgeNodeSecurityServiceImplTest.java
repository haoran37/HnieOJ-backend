package com.hnieacm.judge.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.judge.dto.ValidateJudgeNodeTokenRequest;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeAuthCodeMapper;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.properties.JudgeSecurityProperties;
import com.hnieacm.judge.service.FormalJudgeTokenService;
import com.hnieacm.judge.vo.JudgeNodeTokenValidationVo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 判题节点安全服务测试：旧 bearer/共享 formalToken 校验通路已退役，
 * 任何非 Ed25519 请求都必须被拒绝。
 */
@ExtendWith(MockitoExtension.class)
class JudgeNodeSecurityServiceImplTest {

    /** 运行时随机生成的非生产测试秘密，避免仓库携带可被扫描命中的固定密钥字面量。 */
    private static final String JWT_SECRET = java.util.UUID.randomUUID().toString().replace("-", "");

    @Mock
    private JudgeNodeAuthCodeMapper authCodeMapper;

    @Mock
    private JudgeNodeTokenMapper tokenMapper;

    @Mock
    private FormalJudgeTokenService formalJudgeTokenService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private JudgeNodeSecurityServiceImpl service;

    @BeforeEach
    void setUp() {
        JudgeSecurityProperties properties = new JudgeSecurityProperties();
        properties.setJwtSecret(JWT_SECRET);
        properties.setTempTokenAllowedClockSkewSeconds(300);
        properties.setTempTokenNonceTtlSeconds(600);
        service = new JudgeNodeSecurityServiceImpl(authCodeMapper, tokenMapper, properties,
                formalJudgeTokenService, new ObjectMapper(), stringRedisTemplate);
        initTableInfo(JudgeNodeToken.class);
    }

    @Test
    void shouldAlwaysRejectRetiredBearerTokenValidation() {
        ValidateJudgeNodeTokenRequest request = new ValidateJudgeNodeTokenRequest();
        request.setBearerToken("legacy-bearer-token");
        request.setJudgeToken("legacy-shared-formal-token");
        request.setMethod("GET");
        request.setPathWithQuery("/judge/problems/1/testdata");

        JudgeNodeTokenValidationVo result = service.validateToken(request);

        assertThat(result.getValid()).isFalse();
        assertThat(result.getNodeId()).isNull();
        assertThat(result.getTokenId()).isNull();
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
