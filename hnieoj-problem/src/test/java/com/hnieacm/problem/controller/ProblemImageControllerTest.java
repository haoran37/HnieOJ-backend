package com.hnieacm.problem.controller;

import com.hnieacm.common.exception.GlobalExceptionHandler;
import com.hnieacm.problem.properties.ProblemStorageProperties;
import com.hnieacm.problem.service.impl.ProblemFileStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 题面图片 Controller 回归：真实读取落盘 PNG 返回正确字节/MIME/nosniff，
 * 缺失文件与非图片后缀由全局异常处理器返回业务错误码。
 */
class ProblemImageControllerTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02, 0x03
    };

    @TempDir
    Path root;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProblemStorageProperties properties = new ProblemStorageProperties();
        properties.setRootPath(root.toString());
        ProblemFileStorageServiceImpl storageService = new ProblemFileStorageServiceImpl(properties);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProblemImageController(storageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void servesRealImageBytesWithImageMimeAndNosniff() throws Exception {
        Path images = Files.createDirectories(root.resolve("8").resolve("images"));
        Files.write(images.resolve("one.png"), PNG_BYTES);

        mockMvc.perform(get("/oj/images/8/one.png"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PNG_BYTES));
    }

    @Test
    void missingImageReturnsNotFoundCode() throws Exception {
        mockMvc.perform(get("/oj/images/8/missing.png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void nonImageSuffixIsRejectedAndNotServed() throws Exception {
        Path images = Files.createDirectories(root.resolve("8").resolve("images"));
        Files.writeString(images.resolve("proof.txt"), "secret");

        mockMvc.perform(get("/oj/images/8/proof.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
