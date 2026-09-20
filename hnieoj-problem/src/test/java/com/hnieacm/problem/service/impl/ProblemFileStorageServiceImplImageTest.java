package com.hnieacm.problem.service.impl;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.properties.ProblemStorageProperties;
import com.hnieacm.problem.service.ProblemFileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 题面图片读取回归：真实落盘后按字节读取、删除后 404、
 * 拒绝路径穿越/非图片后缀，且 testdata 目录不可通过图片入口读取。
 */
class ProblemFileStorageServiceImplImageTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02, 0x03
    };

    @TempDir
    Path root;

    private ProblemFileStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        ProblemStorageProperties properties = new ProblemStorageProperties();
        properties.setRootPath(root.toString());
        service = new ProblemFileStorageServiceImpl(properties);
    }

    @Test
    void savedImageIsReadBackByteForByteWithImageMime() {
        MockMultipartFile file = new MockMultipartFile("file", "one.png", "image/png", PNG_BYTES);
        String url = service.saveImage(8L, file);
        assertThat(url).startsWith("/oj/images/8/");

        String filename = url.substring(url.lastIndexOf('/') + 1);
        ProblemFileStorageService.ProblemImageContent content = service.readImage(8L, filename);
        assertThat(content.content()).isEqualTo(PNG_BYTES);
        assertThat(content.contentType()).isEqualTo("image/png");
    }

    @Test
    void deletedImageIsNoLongerReadable() throws IOException {
        Path images = Files.createDirectories(root.resolve("8").resolve("images"));
        Files.write(images.resolve("gone.png"), PNG_BYTES);

        assertThat(service.readImage(8L, "gone.png").content()).isEqualTo(PNG_BYTES);
        service.deleteImage(8L, "gone.png");
        assertThatThrownBy(() -> service.readImage(8L, "gone.png"))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.NOT_FOUND));
    }

    @Test
    void pathTraversalFilenamesAreRejected() {
        assertBadRequest("../testdata/1.in");
        assertBadRequest("..");
        assertBadRequest("sub/one.png");
        assertBadRequest("sub\\one.png");
    }

    @Test
    void nonImageSuffixesAreRejected() throws IOException {
        Path images = Files.createDirectories(root.resolve("8").resolve("images"));
        Files.writeString(images.resolve("proof.txt"), "not an image");
        Files.writeString(images.resolve("payload.svg"), "<svg onload=alert(1)></svg>");
        Files.write(images.resolve("noext"), new byte[]{1});

        assertBadRequest("proof.txt");
        assertBadRequest("payload.svg");
        assertBadRequest("noext");
    }

    @Test
    void testdataDirectoryIsNotReachableThroughImageEndpoint() throws IOException {
        Path testdata = Files.createDirectories(root.resolve("8").resolve("testdata"));
        Files.writeString(testdata.resolve("1.in"), "secret answer");
        Files.writeString(testdata.resolve("answer.png"), "not really an image");

        // 同名图片只会落在 images 目录，不会命中 testdata
        assertThatThrownBy(() -> service.readImage(8L, "answer.png"))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.NOT_FOUND));
        assertBadRequest("../testdata/1.in");
    }

    @Test
    void missingImageAndInvalidProblemIdAreRejected() {
        assertThatThrownBy(() -> service.readImage(8L, "missing.png"))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.NOT_FOUND));
        assertThatThrownBy(() -> service.readImage(0L, "one.png"))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.BAD_REQUEST));
    }

    private void assertBadRequest(String filename) {
        assertThatThrownBy(() -> service.readImage(8L, filename))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.BAD_REQUEST));
    }
}
