package com.balancify.backend.api.group;

import com.balancify.backend.api.MmrMaskingMapper;
import com.balancify.backend.api.group.dto.RankingItemResponse;
import com.balancify.backend.security.AdminRequestResolver;
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
    private final AdminRequestResolver adminRequestResolver;

    public GroupRankingController(
        RankingService rankingService,
        AdminRequestResolver adminRequestResolver
    ) {
        this.rankingService = rankingService;
        this.adminRequestResolver = adminRequestResolver;
    }

    @GetMapping("/{groupId}/ranking")
    public List<RankingItemResponse> getGroupRanking(
        @PathVariable Long groupId,
        HttpServletRequest request
    ) {
        List<RankingItemResponse> response = rankingService.getGroupRanking(groupId);
        if (adminRequestResolver.isAdminRequest(request)) {
            return response;
        }

        return MmrMaskingMapper.maskRanking(response);
    }
}
