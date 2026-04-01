package com.balancify.backend.api.group;

import com.balancify.backend.api.MmrMaskingMapper;
import com.balancify.backend.api.group.dto.GroupRecentMatchResponse;
import com.balancify.backend.security.AdminRequestResolver;
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
    private final AdminRequestResolver adminRequestResolver;

    public GroupMatchController(
        MatchQueryService matchQueryService,
        AdminRequestResolver adminRequestResolver
    ) {
        this.matchQueryService = matchQueryService;
        this.adminRequestResolver = adminRequestResolver;
    }

    @GetMapping("/{groupId}/matches/recent")
    public List<GroupRecentMatchResponse> getRecentMatches(
        @PathVariable Long groupId,
        @RequestParam(required = false) Integer limit,
        HttpServletRequest request
    ) {
        List<GroupRecentMatchResponse> response = matchQueryService.getRecentMatches(groupId, limit);
        if (adminRequestResolver.isAdminRequest(request)) {
            return response;
        }

        return MmrMaskingMapper.maskRecentMatches(response);
    }
}
