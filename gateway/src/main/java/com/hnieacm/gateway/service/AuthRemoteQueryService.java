package com.hnieacm.gateway.service;

import com.hnieacm.common.constant.HeaderConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: 鉴权数据远程查询。
 * @Attention: 本类为缓存未命中的远程调用，会在 Reactor 线程上进行阻塞等待，仅适用于缓存未命中场景，请勿在缓存命中场景下使用
 */
@Slf4j
@Service
@RefreshScope
@RequiredArgsConstructor
public class AuthRemoteQueryService {

    private final WebClient.Builder webClientBuilder;

    @Value("${hnieoj.internal.token:}")
    private String internalToken;

    /**
     * @MethodName queryRoles
     * @Param uid
     * @Description 远程查询角色
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    public List<String> queryRoles(String uid) {
        return queryList("/internal/auth/roles/" + uid, uid, "roles");
    }

    /**
     * @MethodName queryPermissions
     * @Param uid
     * @Description 远程查询权限
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    public List<String> queryPermissions(String uid) {
        return queryList("/internal/auth/permissions/" + uid, uid, "permissions");
    }

    /**
     * @MethodName queryList
     * @Param path
     * @Param uid
     * @Param dataType
     * @Description 远程查询列表
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    private List<String> queryList(String path, String uid, String dataType) {
        try {
            WebClient.RequestHeadersSpec<?> spec = webClientBuilder.build()
                    .get()
                    .uri("lb://hnieoj-auth" + path);
            if (internalToken != null && !internalToken.isBlank()) {
                spec = spec.header(HeaderConstant.INTERNAL_TOKEN, internalToken);
            }

            Result<List<String>> result = spec.retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Result<List<String>>>() {
                    })
                    // 订阅放到 boundedElastic，避免把订阅/回调绑定在当前 event-loop 上
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(Duration.ofSeconds(2))
                    // 禁用 Mono.block()，使用 Future 等待，避免触发 Reactor 的 "block not supported" 检测
                    .toFuture()
                    .get(Duration.ofSeconds(2).toMillis() + 200, TimeUnit.MILLISECONDS);

            if (result == null) {
                return Collections.emptyList();
            }
            if (result.getCode() != ResultCode.SUCCESS) {
                log.warn("Remote query {} failed, uid: {}, code: {}, msg: {}", dataType, uid, result.getCode(), result.getMsg());
                return Collections.emptyList();
            }
            return result.getData() == null ? Collections.emptyList() : result.getData();
        } catch (Exception e) {
            log.warn("Remote query {} failed, uid: {}", dataType, uid, e);
            return Collections.emptyList();
        }
    }
}

