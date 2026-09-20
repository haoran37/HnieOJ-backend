package com.hnieacm.problem.controller;

import com.hnieacm.problem.service.ProblemFileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 题面图片读取接口（默认相对 imageUrlPrefix=/oj/images）
 */
@Tag(name = "题目图片模块")
@Validated
@RestController
@RequestMapping("/oj/images")
@RequiredArgsConstructor
public class ProblemImageController {

    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    private static final String NOSNIFF = "nosniff";

    private final ProblemFileStorageService fileStorageService;

    /**
     * @MethodName readImage
     * @Param id
     * @Param filename
     * @Description 读取 root/{id}/images 下的单张题面图片，返回图片 MIME 与 nosniff
     * @Return @return {@link ResponseEntity }<{@link byte[] }>
     * @Author HaoRan_Lyu
     * @Date 2026/09/20
     */
    @Operation(summary = "读取题面图片")
    @GetMapping("/{id}/{filename:.+}")
    public ResponseEntity<byte[]> readImage(@PathVariable @Min(value = 1, message = "id 必须大于 0") Long id,
                                            @PathVariable String filename) {
        ProblemFileStorageService.ProblemImageContent image = fileStorageService.readImage(id, filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header(X_CONTENT_TYPE_OPTIONS, NOSNIFF)
                .body(image.content());
    }
}
