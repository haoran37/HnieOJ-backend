package com.hnieacm.achievement.service.impl;

import com.hnieacm.achievement.properties.AchievementFileProperties;
import com.hnieacm.achievement.service.AchievementFileService;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 成就附件本地读取回归：真实落盘后按字节读取，缺失/外部 URL/路径穿越均被拒绝。
 */
class AchievementFileServiceImplTest {

    @TempDir
    Path uploadDir;

    private AchievementFileServiceImpl service;

    @BeforeEach
    void setUp() {
        AchievementFileProperties properties = new AchievementFileProperties();
        properties.setUploadDir(uploadDir.toString());
        service = new AchievementFileServiceImpl(properties);
    }

    @Test
    void storedLocalFileIsLoadedByteForByte() {
        byte[] content = "integration proof".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "proof.txt", "text/plain", content);

        String key = service.store("20230001", file);

        assertThat(key).doesNotContain("/").doesNotContain("\\");
        AchievementFileService.LocalFile loaded = service.loadLocal(key);
        assertThat(loaded.content()).isEqualTo(content);
        assertThat(loaded.filename()).isEqualTo(key);
    }

    @Test
    void missingLocalFileReturnsNotFound() {
        assertThatThrownBy(() -> service.loadLocal("20230001_missing.txt"))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.NOT_FOUND));
    }

    @Test
    void externalUrlsAreNotProxiedByServer() {
        assertBadRequest("https://cdn.example.com/proof.pdf");
        assertBadRequest("http://cdn.example.com/proof.pdf");
    }

    @Test
    void traversalKeysAreRejected() {
        assertBadRequest("../secret.txt");
        assertBadRequest("sub/proof.txt");
        assertBadRequest("sub\\proof.txt");
    }

    @Test
    void publicUrlPrefixOutputIsTreatedAsExternal() {
        AchievementFileProperties properties = new AchievementFileProperties();
        properties.setUploadDir(uploadDir.toString());
        properties.setPublicUrlPrefix("https://cdn.example.com/achievements");
        AchievementFileServiceImpl prefixed = new AchievementFileServiceImpl(properties);

        String url = prefixed.store("20230001",
                new MockMultipartFile("file", "proof.txt", "text/plain", new byte[]{1}));

        assertThat(url).startsWith("https://cdn.example.com/achievements/");
        assertThatThrownBy(() -> prefixed.loadLocal(url))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.BAD_REQUEST));
    }

    private void assertBadRequest(String storedValue) {
        assertThatThrownBy(() -> service.loadLocal(storedValue))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.BAD_REQUEST));
    }
}
