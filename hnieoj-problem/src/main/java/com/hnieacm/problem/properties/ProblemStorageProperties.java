package com.hnieacm.problem.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

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

    private DataSize maxTestdataZipSize = DataSize.ofMegabytes(50);

    private DataSize maxTestdataUncompressedSize = DataSize.ofMegabytes(200);

    private int maxTestdataFileCount = 2000;
}
