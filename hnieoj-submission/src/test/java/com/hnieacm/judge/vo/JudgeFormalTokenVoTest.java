package com.hnieacm.judge.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeFormalTokenVoTest {

    @Test
    void shouldNotExposeTokenOrTokenStorageLocation() throws Exception {
        Set<String> fieldNames = Arrays.stream(JudgeFormalTokenVo.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(fieldNames).doesNotContain(
                "token",
                "rawToken",
                "encryptedToken",
                "tokenHash",
                "nacosDataId",
                "nacosGroup"
        );

        JudgeFormalTokenVo vo = new JudgeFormalTokenVo();
        vo.setId(1L);
        vo.setVersion(2);
        vo.setStatus("active");

        JsonNode json = new ObjectMapper().valueToTree(vo);

        assertThat(json.has("id")).isTrue();
        assertThat(json.has("version")).isTrue();
        assertThat(json.has("status")).isTrue();
        assertThat(json.has("token")).isFalse();
        assertThat(json.has("encryptedToken")).isFalse();
        assertThat(json.has("nacosDataId")).isFalse();
        assertThat(json.has("nacosGroup")).isFalse();
    }
}
