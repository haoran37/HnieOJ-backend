package com.hnieacm.discussion.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hnieacm.common.result.Result;
import com.hnieacm.discussion.mapper.DiscussionMapper;
import com.hnieacm.discussion.feign.ContributionUserClient;
import com.hnieacm.discussion.feign.dto.UserBasicDto;
import com.hnieacm.discussion.vo.ContributionRankVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author HnieOJ contributors
 */
@RestController
@SaCheckLogin
@RequestMapping("/api/discussions/contributions")
@RequiredArgsConstructor
public class ContributionController {
    private final DiscussionMapper discussionMapper;
    private final ContributionUserClient userClient;

    @GetMapping
    public Result<List<ContributionRankVo>> list() {
        List<ContributionRankVo> rows = discussionMapper.listContributions();
        fillUsernames(rows);
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setRank(i + 1);
        }
        return Result.success(rows);
    }

    @GetMapping("/{uid}")
    public Result<ContributionRankVo> detail(@PathVariable String uid) {
        ContributionRankVo row = discussionMapper.getContribution(uid);
        if (row != null) {
            fillUsernames(List.of(row));
        }
        return Result.success(row);
    }

    private void fillUsernames(List<ContributionRankVo> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Result<List<UserBasicDto>> response = userClient.query(new ContributionUserClient.UserBatchQueryRequest(
                rows.stream().map(ContributionRankVo::getUid).toList()));
        Map<String, String> names = response == null || response.getData() == null ? Map.of()
                : response.getData().stream().filter(user -> user.getUid() != null && user.getUsername() != null)
                .collect(Collectors.toMap(UserBasicDto::getUid, UserBasicDto::getUsername, (first, ignored) -> first));
        rows.forEach(row -> row.setUsername(names.getOrDefault(row.getUid(), row.getUid())));
    }
}
