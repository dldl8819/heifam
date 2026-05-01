package com.balancify.backend.api.group;

import com.balancify.backend.api.MmrMaskingMapper;
import com.balancify.backend.api.group.dto.GroupRecentMatchResponse;
import com.balancify.backend.security.MmrAccessRequestResolver;
import com.balancify.backend.service.MatchQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupMatchController {

    private final MatchQueryService matchQueryService;
    private final MmrAccessRequestResolver mmrAccessRequestResolver;

    public GroupMatchController(
        MatchQueryService matchQueryService,
        MmrAccessRequestResolver mmrAccessRequestResolver
    ) {
        this.matchQueryService = matchQueryService;
        this.mmrAccessRequestResolver = mmrAccessRequestResolver;
    }

    @GetMapping("/{groupId}/matches/recent")
    public List<GroupRecentMatchResponse> getRecentMatches(
        @PathVariable Long groupId,
        @RequestParam(required = false) Integer limit,
        HttpServletRequest request
    ) {
        List<GroupRecentMatchResponse> response = matchQueryService.getRecentMatches(groupId, limit);
        if (mmrAccessRequestResolver.canViewMmr(request)) {
            return response;
        }

        return MmrMaskingMapper.maskRecentMatches(response);
    }
}
