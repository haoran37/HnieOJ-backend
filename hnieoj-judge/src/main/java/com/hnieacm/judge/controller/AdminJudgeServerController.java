package com.hnieacm.judge.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.constant.JudgeNodeConstant;
import com.hnieacm.judge.service.JudgeNodeSecurityService;
import com.hnieacm.judge.vo.JudgeNodeTokenVo;
import com.hnieacm.judge.vo.JudgeServerVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: Legacy judge server API.
 */
@Tag(name = "Admin Judge Server")
@RestController
@RequestMapping("/api/admin/judge")
@RequiredArgsConstructor
@SaCheckRole(value = {RoleConstant.ADMIN, RoleConstant.ROOT}, mode = SaMode.OR)
public class AdminJudgeServerController {

    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_DISABLED = 1;

    private final JudgeNodeSecurityService judgeNodeSecurityService;

    @Operation(summary = "Query judge servers")
    @GetMapping("/servers")
    public Result<List<JudgeServerVo>> listServers(@RequestParam(required = false) Integer status) {
        if (status != null && status != STATUS_NORMAL && status != STATUS_DISABLED) {
            throw new BizException(ResultCode.BAD_REQUEST, "status must be 0 or 1");
        }
        List<JudgeServerVo> servers = judgeNodeSecurityService.listTokens(null).stream()
                .filter(item -> matchStatus(item, status))
                .map(this::toServerVo)
                .toList();
        return Result.success(servers);
    }

    private boolean matchStatus(JudgeNodeTokenVo token, Integer status) {
        if (status == null) {
            return true;
        }
        boolean active = JudgeNodeConstant.TOKEN_ACTIVE.equals(token.getStatus());
        return status == STATUS_NORMAL ? active : !active;
    }

    private JudgeServerVo toServerVo(JudgeNodeTokenVo token) {
        JudgeServerVo vo = new JudgeServerVo();
        vo.setId(token.getId());
        vo.setName(resolveName(token));
        vo.setIp(null);
        vo.setPort(null);
        vo.setTaskNumber(token.getRunningTasks());
        vo.setMaxTaskNumber(token.getMaxConcurrency());
        vo.setStatus(JudgeNodeConstant.TOKEN_ACTIVE.equals(token.getStatus()) ? STATUS_NORMAL : STATUS_DISABLED);
        vo.setGmtCreate(token.getGmtCreate());
        vo.setNodeId(token.getNodeId());
        vo.setNodeType(token.getNodeType());
        vo.setOnline(token.getOnline());
        return vo;
    }

    private String resolveName(JudgeNodeTokenVo token) {
        if (token.getNodeName() != null && !token.getNodeName().isBlank()) {
            return token.getNodeName();
        }
        return token.getNodeId();
    }
}
