package com.hnieacm.problem.service.impl;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.problem.properties.ProblemStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: Problem image storage validation tests.
 */
class ProblemFileStorageServiceImplTest {

    @TempDir
    private Path tempDir;

    private ProblemStorageProperties storageProperties;
    private ProblemFileStorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        storageProperties = new ProblemStorageProperties();
        storageProperties.setRootPath(tempDir.toString());
        storageProperties.setImageUrlPrefix("/oj/images");
        storageService = new ProblemFileStorageServiceImpl(storageProperties);
    }

    @Test
    void shouldSaveAllowedImageWithGeneratedFilename() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "file",
                "statement.PNG",
                "image/png",
                new byte[]{1, 2, 3}
        );

        String url = storageService.saveImage(10L, image);

        assertThat(url).matches("/oj/images/10/[A-F0-9]{32}\\.png");
        Path imageDir = tempDir.resolve("10").resolve("images");
        try (Stream<Path> files = Files.list(imageDir)) {
            assertThat(files.toList()).hasSize(1);
        }
    }

    @Test
    void shouldRejectUnsupportedImageExtension() {
        MockMultipartFile image = new MockMultipartFile(
                "file",
                "statement.txt",
                "image/png",
                new byte[]{1}
        );

        assertThatThrownBy(() -> storageService.saveImage(1L, image))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectUnsupportedImageContentType() {
        MockMultipartFile image = new MockMultipartFile(
                "file",
                "statement.png",
                "text/plain",
                new byte[]{1}
        );

        assertThatThrownBy(() -> storageService.saveImage(1L, image))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectImageExceedingConfiguredSize() {
        storageProperties.setMaxImageSize(DataSize.ofBytes(1));
        storageProperties.setAllowedImageExtensions(List.of("png"));
        storageProperties.setAllowedImageContentTypes(List.of("image/png"));
        MockMultipartFile image = new MockMultipartFile(
                "file",
                "statement.png",
                "image/png",
                new byte[]{1, 2}
        );

        assertThatThrownBy(() -> storageService.saveImage(1L, image))
                .isInstanceOf(BizException.class);
    }
}
