package com.hnieacm.judge.controller;

import com.hnieacm.common.result.Result;
import com.hnieacm.judge.dto.ExchangeJudgeTempTokenRequest;
import com.hnieacm.judge.service.JudgeNodeSecurityService;
import com.hnieacm.judge.vo.JudgeTempTokenVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点认证接口
 */
@Tag(name = "判题节点认证模块")
@Validated
@RestController
@RequestMapping("/api/judge")
@RequiredArgsConstructor
public class JudgeNodeAuthController {

    private final JudgeNodeSecurityService judgeNodeSecurityService;

    @Operation(summary = "临时判题节点兑换短期 Token")
    @PostMapping("/temp-token")
    public Result<JudgeTempTokenVo> exchangeTempToken(@Valid @RequestBody ExchangeJudgeTempTokenRequest request) {
        return Result.success(judgeNodeSecurityService.exchangeTempToken(request));
    }
}
