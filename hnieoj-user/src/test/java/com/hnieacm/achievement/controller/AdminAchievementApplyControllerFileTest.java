package com.hnieacm.achievement.controller;

import com.hnieacm.achievement.service.AchievementApplyService;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.exception.GlobalExceptionHandler;
import com.hnieacm.common.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 成就附件下载 Controller 回归：attachment + octet-stream + nosniff，
 * 缺失附件返回业务 404 而非文件系统内容。
 */
@ExtendWith(MockitoExtension.class)
class AdminAchievementApplyControllerFileTest {

    @Mock
    private AchievementApplyService achievementApplyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminAchievementApplyController(achievementApplyService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void downloadsLocalAttachmentAsAttachmentOctetStream() throws Exception {
        byte[] content = "integration proof".getBytes(StandardCharsets.UTF_8);
        when(achievementApplyService.downloadFile(9L)).thenReturn(
                new AchievementApplyService.AchievementFileDownload(
                        content, "20230001_a.txt", "application/octet-stream"));

        mockMvc.perform(get("/api/admin/achievements/9/file"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"20230001_a.txt\""))
                .andExpect(content().bytes(content));
    }

    @Test
    void missingAttachmentReturnsBusinessNotFoundCode() throws Exception {
        when(achievementApplyService.downloadFile(10L))
                .thenThrow(new BizException(ResultCode.NOT_FOUND, "附件不存在"));

        mockMvc.perform(get("/api/admin/achievements/10/file"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }
}
