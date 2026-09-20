package com.hnieacm.achievement.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.achievement.entity.AchievementApply;
import com.hnieacm.achievement.mapper.AchievementApplyMapper;
import com.hnieacm.achievement.mapper.UserAchievementMapper;
import com.hnieacm.achievement.properties.AchievementFileProperties;
import com.hnieacm.achievement.service.AchievementApplyService;
import com.hnieacm.achievement.vo.AchievementApplyAdminVo;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.mapper.UserInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 成就审核 VO 与附件下载回归：本地 key 转受保护下载地址、外部地址原样保留，
 * 本地附件按字节读取、缺失文件 404、外部地址不代理。
 */
@ExtendWith(MockitoExtension.class)
class AchievementApplyServiceImplAdminFileTest {

    @Mock
    private AchievementApplyMapper achievementApplyMapper;

    @Mock
    private UserAchievementMapper userAchievementMapper;

    @Mock
    private UserInfoMapper userInfoMapper;

    @TempDir
    Path uploadDir;

    private AchievementApplyServiceImpl service;

    @BeforeEach
    void setUp() {
        AchievementFileProperties properties = new AchievementFileProperties();
        properties.setUploadDir(uploadDir.toString());
        AchievementFileServiceImpl fileService = new AchievementFileServiceImpl(properties);
        service = new AchievementApplyServiceImpl(
                achievementApplyMapper, userAchievementMapper, userInfoMapper, fileService);
    }

    @Test
    void adminListExposesDescriptionAndRewritesLocalFileUrl() {
        AchievementApplyAdminVo local = vo(11L, "20230001_local.txt", "local description");
        AchievementApplyAdminVo external = vo(12L, "https://cdn.example.com/a.pdf", "external description");
        Page<AchievementApplyAdminVo> page = new Page<>(1, 10);
        page.setRecords(List.of(local, external));
        page.setTotal(2);
        when(achievementApplyMapper.selectAdminApplyPage(any(), any(), any(), any())).thenReturn(page);

        PageVo<AchievementApplyAdminVo> result = service.listForAdmin(1, 10, null, null, null);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getList().get(0).getTitle()).isEqualTo("title");
        assertThat(result.getList().get(0).getDescription()).isEqualTo("local description");
        assertThat(result.getList().get(0).getFileUrl()).isEqualTo("/api/admin/achievements/11/file");
        assertThat(result.getList().get(1).getDescription()).isEqualTo("external description");
        assertThat(result.getList().get(1).getFileUrl()).isEqualTo("https://cdn.example.com/a.pdf");
    }

    @Test
    void localAttachmentIsDownloadedByteForByte() throws Exception {
        byte[] content = "integration proof".getBytes(StandardCharsets.UTF_8);
        String key = "20230001_1_abc.txt";
        Files.write(uploadDir.resolve(key), content);
        when(achievementApplyMapper.selectById(21L)).thenReturn(apply(21L, key));

        AchievementApplyService.AchievementFileDownload download = service.downloadFile(21L);

        assertThat(download.content()).isEqualTo(content);
        assertThat(download.filename()).isEqualTo(key);
        assertThat(download.contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void missingLocalAttachmentReturnsNotFound() {
        when(achievementApplyMapper.selectById(22L)).thenReturn(apply(22L, "missing.txt"));

        assertThatThrownBy(() -> service.downloadFile(22L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.NOT_FOUND));
    }

    @Test
    void missingApplyReturnsNotFound() {
        when(achievementApplyMapper.selectById(23L)).thenReturn(null);

        assertThatThrownBy(() -> service.downloadFile(23L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.NOT_FOUND));
    }

    @Test
    void externalAttachmentIsNotProxied() {
        when(achievementApplyMapper.selectById(24L)).thenReturn(apply(24L, "https://cdn.example.com/a.pdf"));

        assertThatThrownBy(() -> service.downloadFile(24L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.BAD_REQUEST));
    }

    private static AchievementApplyAdminVo vo(Long id, String fileUrl, String description) {
        AchievementApplyAdminVo vo = new AchievementApplyAdminVo();
        vo.setId(id);
        vo.setUid("20230001");
        vo.setUsername("tester");
        vo.setTitle("title");
        vo.setStatus("pending");
        vo.setDescription(description);
        vo.setFileUrl(fileUrl);
        vo.setSubmitTime(1L);
        return vo;
    }

    private static AchievementApply apply(Long id, String fileUrl) {
        AchievementApply apply = new AchievementApply();
        apply.setId(id);
        apply.setUid("20230001");
        apply.setTitle("title");
        apply.setDescription("description");
        apply.setFileUrl(fileUrl);
        apply.setStatus("pending");
        return apply;
    }
}
