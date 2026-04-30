package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.RankingItemResponse;
import com.balancify.backend.security.SuperAdminRequestResolver;
import com.balancify.backend.service.RankingService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/groups")
public class GroupRankingController {

    private final RankingService rankingService;
    private final SuperAdminRequestResolver superAdminRequestResolver;

    public GroupRankingController(
        RankingService rankingService,
        SuperAdminRequestResolver superAdminRequestResolver
    ) {
        this.rankingService = rankingService;
        this.superAdminRequestResolver = superAdminRequestResolver;
    }

    @GetMapping("/{groupId}/ranking")
    public List<RankingItemResponse> getGroupRanking(
        @PathVariable Long groupId,
        HttpServletRequest request
    ) {
        if (!superAdminRequestResolver.isSuperAdminRequest(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "랭킹은 최고 관리자만 조회할 수 있습니다.");
        }

        return rankingService.getGroupRanking(groupId);
    }
}
