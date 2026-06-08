package com.hnieacm.problem.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 题目本地资源存储配置
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "hnieoj.problem.storage")
public class ProblemStorageProperties {

    private String rootPath = "/data/oj/problems";

    private String imageUrlPrefix = "/oj/images";

    private DataSize maxImageSize = DataSize.ofMegabytes(5);

    private List<String> allowedImageExtensions = List.of("jpg", "jpeg", "png", "gif", "webp");

    private List<String> allowedImageContentTypes = List.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    private DataSize maxTestdataZipSize = DataSize.ofMegabytes(50);

    private DataSize maxTestdataUncompressedSize = DataSize.ofMegabytes(200);

    private int maxTestdataFileCount = 2000;
}
