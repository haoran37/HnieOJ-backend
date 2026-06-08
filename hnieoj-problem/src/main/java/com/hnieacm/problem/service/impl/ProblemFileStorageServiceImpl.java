package com.hnieacm.problem.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.properties.ProblemStorageProperties;
import com.hnieacm.problem.service.ProblemFileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 题目本地文件存储服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemFileStorageServiceImpl implements ProblemFileStorageService {

    private static final String IMAGES_DIR = "images";
    private static final String TESTDATA_DIR = "testdata";
    private static final String TESTDATA_IN_SUFFIX = ".in";
    private static final String TESTDATA_OUT_SUFFIX = ".out";
    private static final int BUFFER_SIZE = 8192;
    private static final int IMAGE_FILENAME_GENERATE_MAX_RETRY = 10;

    private final ProblemStorageProperties storageProperties;

    /**
     * @MethodName initializeProblemResources
     * @Param problemId
     * @Description 初始化问题资源
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public void initializeProblemResources(Long problemId) {
        Path problemDir = resolveProblemDir(problemId);
        try {
            Files.createDirectories(ensureChildPath(problemDir, IMAGES_DIR));
            Files.createDirectories(ensureChildPath(problemDir, TESTDATA_DIR));
        } catch (IOException e) {
            log.error("Initialize problem resources failed, problemId: {}", problemId, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "初始化题目资源目录失败");
        }
    }

    /**
     * @MethodName deleteProblemResources
     * @Param problemId
     * @Description 删除问题资源
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public void deleteProblemResources(Long problemId) {
        Path problemDir = resolveProblemDir(problemId);
        if (!Files.exists(problemDir)) {
            return;
        }
        try {
            deleteDirectory(problemDir);
        } catch (IOException e) {
            log.error("Delete problem resources failed, problemId: {}", problemId, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "删除题目资源目录失败");
        }
    }

    /**
     * @MethodName saveImage
     * @Param problemId
     * @Param file
     * @Description 保存图片
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public String saveImage(Long problemId, MultipartFile file) {
        checkImageUploadFile(file);
        String originalFilename = normalizeFilename(file.getOriginalFilename());
        Path imageDir = resolveProblemPath(problemId, IMAGES_DIR);
        Path targetPath = generateImageStoragePath(imageDir, originalFilename);
        String filename = targetPath.getFileName().toString();
        try {
            Files.createDirectories(imageDir);
            file.transferTo(targetPath);
        } catch (IOException e) {
            log.error("Save problem image failed, problemId: {}, filename: {}", problemId, filename, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "保存图片失败");
        }
        String prefix = StrUtil.removeSuffix(storageProperties.getImageUrlPrefix(), "/");
        return prefix + "/" + problemId + "/" + filename;
    }

    private void checkImageUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "图片文件不能为空");
        }
        if (file.getSize() > storageProperties.getMaxImageSize().toBytes()) {
            throw new BizException(ResultCode.BAD_REQUEST, "图片文件超过大小限制");
        }

        String originalFilename = normalizeFilename(file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (StrUtil.isBlank(extension) || !storageProperties.getAllowedImageExtensions().contains(extension.toLowerCase())) {
            throw new BizException(ResultCode.BAD_REQUEST, "图片文件扩展名不支持");
        }

        String contentType = file.getContentType();
        if (StrUtil.isBlank(contentType)
                || !storageProperties.getAllowedImageContentTypes().contains(contentType.toLowerCase())) {
            throw new BizException(ResultCode.BAD_REQUEST, "图片文件类型不支持");
        }
    }

    /**
     * @MethodName generateImageStoragePath
     * @Param imageDir
     * @Param originalFilename
     * @Description 生成图像存储路径
     * @Return @return {@link Path }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private Path generateImageStoragePath(Path imageDir, String originalFilename) {
        for (int i = 0; i < IMAGE_FILENAME_GENERATE_MAX_RETRY; i++) {
            String filename = generateImageStorageFilename(originalFilename);
            Path targetPath = ensureChildPath(imageDir, filename);
            if (!Files.exists(targetPath)) {
                return targetPath;
            }
        }
        throw new BizException(ResultCode.INTERNAL_ERROR, "生成图片文件名失败");
    }

    /**
     * @MethodName generateImageStorageFilename
     * @Param originalFilename
     * @Description 生成图像存储文件名
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private String generateImageStorageFilename(String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String suffix = StrUtil.isBlank(extension) ? "" : "." + extension.toLowerCase();
        return UUID.randomUUID().toString().replace("-", "").toUpperCase() + suffix;
    }

    /**
     * @MethodName deleteImage
     * @Param problemId
     * @Param filename
     * @Description 删除图像
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public void deleteImage(Long problemId, String filename) {
        String normalizedFilename = normalizeFilename(filename);
        Path imageDir = resolveProblemPath(problemId, IMAGES_DIR);
        Path targetPath = ensureChildPath(imageDir, normalizedFilename);
        if (!Files.isRegularFile(targetPath)) {
            throw new BizException(ResultCode.NOT_FOUND, "图片不存在");
        }
        try {
            Files.delete(targetPath);
        } catch (IOException e) {
            log.error("Delete problem image failed, problemId: {}, filename: {}", problemId, normalizedFilename, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "删除图片失败");
        }
    }

    /**
     * @MethodName validateTestdata
     * @Param file
     * @Description 验证测试数据
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public void validateTestdata(MultipartFile file) {
        checkTestdataUploadFile(file);
        readAndValidateTestdataZip(file);
    }

    /**
     * @MethodName replaceTestdata
     * @Param problemId
     * @Param file
     * @Description 替换测试数据
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public void replaceTestdata(Long problemId, MultipartFile file) {
        checkTestdataUploadFile(file);
        Map<String, byte[]> testdata = readAndValidateTestdataZip(file);
        Path problemDir = resolveProblemDir(problemId);
        Path testdataDir = ensureChildPath(problemDir, TESTDATA_DIR);
        Path tempDir = ensureChildPath(
                problemDir,
                TESTDATA_DIR + ".upload." + UUID.randomUUID().toString().replace("-", "")
        );
        try {
            Files.createDirectories(tempDir);
            for (Map.Entry<String, byte[]> entry : testdata.entrySet()) {
                Path targetPath = ensureChildPath(tempDir, entry.getKey());
                Files.write(targetPath, entry.getValue());
            }
            replaceDirectory(testdataDir, tempDir);
        } catch (IOException e) {
            log.error("Replace problem testdata failed, problemId: {}", problemId, e);
            deleteDirectoryQuietly(tempDir);
            throw new BizException(ResultCode.INTERNAL_ERROR, "保存测试数据失败");
        }
    }

    /**
     * @MethodName checkTestdataUploadFile
     * @Param file
     * @Description 测试数据上传文件
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void checkTestdataUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "测试数据 ZIP 不能为空");
        }
        if (file.getSize() > storageProperties.getMaxTestdataZipSize().toBytes()) {
            throw new BizException(ResultCode.BAD_REQUEST, "测试数据 ZIP 不能超过 50MB");
        }
    }

    /**
     * @MethodName writeTestdataZip
     * @Param problemId
     * @Param outputStream
     * @Description 写入测试数据zip
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public void writeTestdataZip(Long problemId, OutputStream outputStream) {
        Path testdataDir = resolveProblemPath(problemId, TESTDATA_DIR);
        List<Path> files = listTestdataFiles(testdataDir);
        if (files.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "测试数据不存在");
        }
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            for (Path file : files) {
                Path safeFile = ensureChildPath(testdataDir, file.getFileName().toString());
                if (!Files.isRegularFile(safeFile)) {
                    continue;
                }
                ZipEntry entry = new ZipEntry(safeFile.getFileName().toString());
                zipOutputStream.putNextEntry(entry);
                try (var inputStream = Files.newInputStream(safeFile)) {
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        zipOutputStream.write(buffer, 0, len);
                    }
                }
                zipOutputStream.closeEntry();
            }
        } catch (IOException e) {
            log.error("Write problem testdata zip failed, problemId: {}", problemId, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "下载测试数据失败");
        }
    }

    /**
     * @MethodName writeTestdataCaseZip
     * @Param problemId
     * @Param caseNo
     * @Param outputStream
     * @Description 写入测试数据案例zip
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public void writeTestdataCaseZip(Long problemId, Integer caseNo, OutputStream outputStream) {
        if (caseNo == null || caseNo <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "测试点编号必须从 1 开始");
        }
        Path testdataDir = resolveProblemPath(problemId, TESTDATA_DIR);
        List<TestdataPair> pairs = listTestdataPairs(testdataDir);
        if (pairs.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "测试数据不存在");
        }
        if (caseNo > pairs.size()) {
            throw new BizException(ResultCode.BAD_REQUEST, "测试点编号超出范围");
        }

        TestdataPair pair = pairs.get(caseNo - 1);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            writeFileToZip(zipOutputStream, pair.inputFile());
            writeFileToZip(zipOutputStream, pair.outputFile());
        } catch (IOException e) {
            log.error("Write problem testdata case zip failed, problemId: {}, caseNo: {}", problemId, caseNo, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "下载测试点失败");
        }
    }

    /**
     * @MethodName hasAvailableTestdata
     * @Param problemId
     * @Description 有可用测试数据
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public boolean hasAvailableTestdata(Long problemId) {
        return countAvailableTestdataCases(problemId) > 0;
    }

    /**
     * @MethodName countAvailableTestdataCases
     * @Param problemId
     * @Description 统计可用测试数据案例
     * @Return @return int
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public int countAvailableTestdataCases(Long problemId) {
        Path testdataDir = resolveProblemPath(problemId, TESTDATA_DIR);
        return listTestdataPairs(testdataDir).size();
    }

    /**
     * @MethodName readAndValidateTestdataZip
     * @Param file
     * @Description 读取并验证测试数据压缩包
     * @Return @return {@link Map }<{@link String }, {@link byte[] }>
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private Map<String, byte[]> readAndValidateTestdataZip(MultipartFile file) {
        Map<String, byte[]> fileMap = new TreeMap<>(Comparator.naturalOrder());
        Map<String, Set<String>> pairMap = new HashMap<>();
        long totalBytes = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    validateZipEntryName(entry.getName());
                    continue;
                }
                String filename = validateZipEntryName(entry.getName());
                if (!isTestdataFile(filename)) {
                    continue;
                }
                if (fileMap.size() >= storageProperties.getMaxTestdataFileCount()) {
                    throw new BizException(ResultCode.BAD_REQUEST, "测试数据文件数量超过限制");
                }
                byte[] bytes = readZipEntry(zipInputStream);
                totalBytes += bytes.length;
                if (totalBytes > storageProperties.getMaxTestdataUncompressedSize().toBytes()) {
                    throw new BizException(ResultCode.BAD_REQUEST, "测试数据解压后总大小超过限制");
                }
                validateUtf8(filename, bytes);
                fileMap.put(filename, bytes);
                recordPair(pairMap, filename);
            }
        } catch (IOException e) {
            log.warn("Parse testdata zip failed", e);
            throw new BizException(ResultCode.BAD_REQUEST, "测试数据 ZIP 格式不正确");
        }

        if (fileMap.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "测试数据 ZIP 中没有有效的 in/out 文件");
        }
        validatePairs(pairMap);
        return fileMap;
    }

    /**
     * @MethodName validateZipEntryName
     * @Param entryName
     * @Description 验证zip条目名称
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private String validateZipEntryName(String entryName) {
        String filename = normalizeFilename(entryName);
        if (!filename.equals(entryName)) {
            throw new BizException(ResultCode.BAD_REQUEST, "测试数据 ZIP 内部路径不合法");
        }
        return filename;
    }

    /**
     * @MethodName readZipEntry
     * @Param zipInputStream
     * @Description 读取zip条目
     * @Return @return {@link byte[] }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private byte[] readZipEntry(ZipInputStream zipInputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int len;
        while ((len = zipInputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
        }
        return outputStream.toByteArray();
    }

    /**
     * @MethodName validateUtf8
     * @Param filename
     * @Param bytes
     * @Description 验证utf8
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void validateUtf8(String filename, byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException e) {
            throw new BizException(ResultCode.BAD_REQUEST, filename + " 不是合法 UTF-8 文本文件");
        }
    }

    /**
     * @MethodName recordPair
     * @Param pairMap
     * @Param filename
     * @Description 记录对
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void recordPair(Map<String, Set<String>> pairMap, String filename) {
        String baseName = filename.substring(0, filename.lastIndexOf('.'));
        String suffix = filename.endsWith(TESTDATA_IN_SUFFIX) ? TESTDATA_IN_SUFFIX : TESTDATA_OUT_SUFFIX;
        pairMap.computeIfAbsent(baseName, key -> new HashSet<>()).add(suffix);
    }

    /**
     * @MethodName validatePairs
     * @Param pairMap
     * @Description 验证配对
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void validatePairs(Map<String, Set<String>> pairMap) {
        for (Map.Entry<String, Set<String>> entry : pairMap.entrySet()) {
            if (!entry.getValue().contains(TESTDATA_IN_SUFFIX) || !entry.getValue().contains(TESTDATA_OUT_SUFFIX)) {
                throw new BizException(ResultCode.BAD_REQUEST, entry.getKey() + " 缺少成对的 in/out 文件");
            }
        }
    }

    /**
     * @MethodName isTestdataFile
     * @Param filename
     * @Description 是测试数据文件
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private boolean isTestdataFile(String filename) {
        return filename.endsWith(TESTDATA_IN_SUFFIX) || filename.endsWith(TESTDATA_OUT_SUFFIX);
    }

    /**
     * @MethodName replaceDirectory
     * @Param targetDir
     * @Param newDir
     * @Description 替换目录
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void replaceDirectory(Path targetDir, Path newDir) throws IOException {
        Path backupDir = ensureChildPath(targetDir.getParent(),
                TESTDATA_DIR + ".backup." + UUID.randomUUID().toString().replace("-", ""));
        try {
            if (Files.exists(targetDir)) {
                moveDirectory(targetDir, backupDir);
            }
            moveDirectory(newDir, targetDir);
            deleteDirectory(backupDir);
        } catch (IOException e) {
            if (!Files.exists(targetDir) && Files.exists(backupDir)) {
                moveDirectory(backupDir, targetDir);
            }
            deleteDirectoryQuietly(backupDir);
            throw e;
        }
    }

    /**
     * @MethodName moveDirectory
     * @Param source
     * @Param target
     * @Description 移动目录
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    /**
     * @MethodName deleteDirectoryQuietly
     * @Param directory
     * @Description 悄悄删除目录
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void deleteDirectoryQuietly(Path directory) {
        try {
            deleteDirectory(directory);
        } catch (IOException e) {
            log.warn("Delete temp directory failed, directory: {}", directory, e);
        }
    }

    /**
     * @MethodName deleteDirectory
     * @Param directory
     * @Description 删除目录
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.delete(path);
            }
        }
    }

    /**
     * @MethodName listTestdataFiles
     * @Param testdataDir
     * @Description 列出测试数据文件
     * @Return @return {@link List }<{@link Path }>
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private List<Path> listTestdataFiles(Path testdataDir) {
        if (!Files.isDirectory(testdataDir)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(testdataDir)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path) && isTestdataFile(path.getFileName().toString())) {
                    files.add(path);
                }
            }
        } catch (IOException e) {
            log.error("List problem testdata failed, testdataDir: {}", testdataDir, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "读取测试数据失败");
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return files;
    }

    /**
     * @MethodName listTestdataPairs
     * @Param testdataDir
     * @Description 列出测试数据对
     * @Return @return {@link List }<{@link TestdataPair }>
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private List<TestdataPair> listTestdataPairs(Path testdataDir) {
        List<Path> files = listTestdataFiles(testdataDir);
        if (files.isEmpty()) {
            return List.of();
        }
        Map<String, TestdataPairBuilder> pairMap = new TreeMap<>(Comparator.naturalOrder());
        for (Path file : files) {
            String filename = file.getFileName().toString();
            String baseName = filename.substring(0, filename.lastIndexOf('.'));
            TestdataPairBuilder builder = pairMap.computeIfAbsent(baseName, key -> new TestdataPairBuilder());
            if (filename.endsWith(TESTDATA_IN_SUFFIX)) {
                builder.inputFile = file;
            } else if (filename.endsWith(TESTDATA_OUT_SUFFIX)) {
                builder.outputFile = file;
            }
        }
        List<TestdataPair> pairs = new ArrayList<>();
        for (Map.Entry<String, TestdataPairBuilder> entry : pairMap.entrySet()) {
            TestdataPairBuilder builder = entry.getValue();
            if (builder.inputFile != null && builder.outputFile != null) {
                pairs.add(new TestdataPair(builder.inputFile, builder.outputFile));
            }
        }
        return pairs;
    }

    /**
     * @MethodName writeFileToZip
     * @Param zipOutputStream
     * @Param file
     * @Description 将文件写入zip
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private void writeFileToZip(ZipOutputStream zipOutputStream, Path file) throws IOException {
        ZipEntry entry = new ZipEntry(file.getFileName().toString());
        zipOutputStream.putNextEntry(entry);
        byte[] buffer = new byte[BUFFER_SIZE];
        try (var inputStream = Files.newInputStream(file)) {
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                zipOutputStream.write(buffer, 0, len);
            }
        }
        zipOutputStream.closeEntry();
    }

    /**
     * @MethodName resolveProblemPath
     * @Param problemId
     * @Param relativePath
     * @Description 解决问题路径
     * @Return @return {@link Path }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private Path resolveProblemPath(Long problemId, String relativePath) {
        return ensureChildPath(resolveProblemDir(problemId), relativePath);
    }

    /**
     * @MethodName resolveProblemDir
     * @Param problemId
     * @Description 解决问题目录
     * @Return @return {@link Path }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private Path resolveProblemDir(Long problemId) {
        if (problemId == null || problemId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "problemId 不合法");
        }
        Path problemDir = rootPath().resolve(String.valueOf(problemId)).normalize();
        if (!problemDir.startsWith(rootPath())) {
            throw new BizException(ResultCode.BAD_REQUEST, "题目资源路径不合法");
        }
        return problemDir;
    }

    /**
     * @MethodName ensureChildPath
     * @Param parent
     * @Param filename
     * @Description 确保 Child 路径
     * @Return @return {@link Path }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private Path ensureChildPath(Path parent, String filename) {
        Path targetPath = parent.resolve(filename).normalize();
        if (!targetPath.startsWith(parent.normalize())) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件路径不合法");
        }
        return targetPath;
    }

    /**
     * @MethodName normalizeFilename
     * @Param filename
     * @Description 规范化文件名
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private String normalizeFilename(String filename) {
        String normalizedFilename = StringUtils.getFilename(StrUtil.nullToEmpty(filename).trim());
        if (StrUtil.isBlank(normalizedFilename) || ".".equals(normalizedFilename) || "..".equals(normalizedFilename)
                || normalizedFilename.contains("/") || normalizedFilename.contains("\\")) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件名不合法");
        }
        return normalizedFilename;
    }

    /**
     * @MethodName rootPath
     *
     * @Description 根路径
     * @Return @return {@link Path }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    private Path rootPath() {
        return Paths.get(storageProperties.getRootPath()).toAbsolutePath().normalize();
    }

    private static class TestdataPairBuilder {
        private Path inputFile;
        private Path outputFile;
    }

    private record TestdataPair(Path inputFile, Path outputFile) {
    }
}
