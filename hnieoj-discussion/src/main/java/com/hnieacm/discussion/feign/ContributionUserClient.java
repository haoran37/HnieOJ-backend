package com.hnieacm.discussion.feign;

import com.hnieacm.common.result.Result;
import com.hnieacm.discussion.feign.dto.UserBasicDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/** Resolves contribution row UIDs to current display names.
 * @author HnieOJ contributors
 */
@FeignClient(name = "hnieoj-user", contextId = "contributionUserClient")
public interface ContributionUserClient {
    /**
     * Resolve a batch of user identifiers.
     * @param request identifiers
     * @return current display names
     */
    @PostMapping("/internal/users/basic-info")
    Result<List<UserBasicDto>> query(@RequestBody UserBatchQueryRequest request);

    record UserBatchQueryRequest(List<String> uids) { }
}
