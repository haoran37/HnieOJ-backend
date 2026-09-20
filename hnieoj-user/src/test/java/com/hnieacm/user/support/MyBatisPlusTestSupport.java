package com.hnieacm.user.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 单元测试辅助：离线初始化 MyBatis-Plus 实体的 TableInfo/列缓存，
 * 使 {@code LambdaQueryWrapper#getTargetSql()} 等不依赖 Spring/数据库的断言可用。
 */
public final class MyBatisPlusTestSupport {

    private MyBatisPlusTestSupport() {
    }

    public static void initTableInfo(Class<?>... entityTypes) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "mybatis-plus-test-support");
        assistant.setCurrentNamespace("com.hnieacm.user.support.MyBatisPlusTestSupport");
        for (Class<?> entityType : entityTypes) {
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
