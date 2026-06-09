package com.hnieacm.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.entity.Problem;
import com.hnieacm.problem.mapper.ProblemMapper;
import com.hnieacm.problem.mapper.ProblemTagMapper;
import com.hnieacm.problem.mapper.TagMapper;
import com.hnieacm.problem.service.ProblemExportService;
import com.hnieacm.problem.service.ProblemFileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 题目导出服务实现
 */
@Service
@RequiredArgsConstructor
public class ProblemExportServiceImpl implements ProblemExportService {

    private static final int MAX_EXPORT_COUNT = 100;

    private final ProblemMapper problemMapper;
    private final ProblemTagMapper problemTagMapper;
    private final TagMapper tagMapper;
    private final ProblemFileStorageService problemFileStorageService;
    private final ObjectMapper objectMapper;

    @Override
    public void exportProblems(List<Long> ids, OutputStream outputStream) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "ids 不能为空");
        }
        List<Long> normalizedIds = ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(MAX_EXPORT_COUNT + 1L)
                .toList();
        if (normalizedIds.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "ids 不合法");
        }
        if (normalizedIds.size() > MAX_EXPORT_COUNT) {
            throw new BizException(ResultCode.BAD_REQUEST, "单次最多导出 100 道题目");
        }

        List<Problem> problems = problemMapper.selectList(
                new LambdaQueryWrapper<Problem>().in(Problem::getId, normalizedIds).orderByAsc(Problem::getId)
        );
        validateProblemExists(normalizedIds, problems);
        Map<Long, List<String>> tagsMap = ProblemServiceSupport.queryTagsMap(problemTagMapper, tagMapper, normalizedIds);

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            List<ProblemExportItem> items = problems.stream()
                    .map(problem -> buildItem(problem, tagsMap.getOrDefault(problem.getId(), List.of())))
                    .toList();
            writeManifest(zipOutputStream, new ProblemExportManifest("hnieoj-problem-export-v1", LocalDateTime.now(), items));
            writeTestdata(zipOutputStream, items);
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "导出题目失败");
        }
    }

    private void validateProblemExists(List<Long> ids, List<Problem> problems) {
        Set<Long> existedIds = new HashSet<>(problems.stream().map(Problem::getId).toList());
        List<Long> missingIds = ids.stream().filter(id -> !existedIds.contains(id)).toList();
        if (!missingIds.isEmpty()) {
            throw new BizException(ResultCode.PROBLEM_NOT_FOUND, "题目不存在：" + missingIds);
        }
    }

    private ProblemExportItem buildItem(Problem problem, List<String> tags) {
        boolean hasTestdata = problemFileStorageService.hasAvailableTestdata(problem.getId());
        String testdataPath = hasTestdata ? "testdata/" + safeName(problem.getProblemCode()) + ".zip" : null;
        return new ProblemExportItem(problem, tags, hasTestdata, testdataPath);
    }

    private void writeManifest(ZipOutputStream zipOutputStream, ProblemExportManifest manifest) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry("manifest.json"));
        zipOutputStream.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
        zipOutputStream.closeEntry();
    }

    private void writeTestdata(ZipOutputStream zipOutputStream, List<ProblemExportItem> items) throws IOException {
        for (ProblemExportItem item : items) {
            if (!item.hasTestdata()) {
                continue;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            problemFileStorageService.writeTestdataZip(item.problem().getId(), buffer);
            zipOutputStream.putNextEntry(new ZipEntry(item.testdataPath()));
            zipOutputStream.write(buffer.toByteArray());
            zipOutputStream.closeEntry();
        }
    }

    private String safeName(String value) {
        return value == null ? "problem" : value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private record ProblemExportManifest(String format, LocalDateTime exportedAt, List<ProblemExportItem> problems) {
    }

    private record ProblemExportItem(Problem problem, List<String> tags, boolean hasTestdata, String testdataPath) {
    }
}
