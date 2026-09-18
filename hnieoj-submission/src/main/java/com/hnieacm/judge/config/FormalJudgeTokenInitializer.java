package com.hnieacm.judge.config;

import com.hnieacm.judge.service.FormalJudgeTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 正式判题节点 Token 自动初始化器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormalJudgeTokenInitializer implements ApplicationRunner {

    private final FormalJudgeTokenService formalJudgeTokenService;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    @Override
    public void run(ApplicationArguments args) {
        initialize();
    }

    @Scheduled(fixedDelayString = "${hnieoj.judge.formal-token.init-retry-delay-ms:60000}")
    public void retryInitialize() {
        if (initialized.get()) {
            return;
        }
        initialize();
    }

    private void initialize() {
        try {
            formalJudgeTokenService.initializeIfNecessary();
            initialized.set(true);
        } catch (Exception e) {
            log.warn("Formal judge token initialize failed, will retry later", e);
        }
    }
}
