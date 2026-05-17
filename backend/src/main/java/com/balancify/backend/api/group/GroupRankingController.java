package com.balancify.backend.api.group;

import com.balancify.backend.api.MmrMaskingMapper;
import com.balancify.backend.api.group.dto.RankingItemResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.RankingService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupRankingController {

    private final RankingService rankingService;
    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public GroupRankingController(
        RankingService rankingService,
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.rankingService = rankingService;
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping("/{groupId}/ranking")
    public List<RankingItemResponse> getGroupRanking(
        @PathVariable Long groupId,
        HttpServletRequest request
    ) {
        AccessControlService.AccessProfile accessProfile = accessControlService.resolveAccessProfile(
            authenticatedRequestResolver.resolve(request).email()
        );

        List<RankingItemResponse> response = rankingService.getGroupRanking(groupId);
        if (accessProfile.superAdmin()) {
            return response;
        }

        return MmrMaskingMapper.maskRanking(response);
    }
}
