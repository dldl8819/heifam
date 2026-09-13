package com.balancify.backend.api.group;

import com.balancify.backend.api.MmrMaskingMapper;
import com.balancify.backend.api.group.dto.GroupMatchPageResponse;
import com.balancify.backend.api.group.dto.GroupRecentMatchResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.security.MmrAccessRequestResolver;
import com.balancify.backend.service.MatchQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/groups")
public class GroupMatchController {

    private final MatchQueryService matchQueryService;
    private final MmrAccessRequestResolver mmrAccessRequestResolver;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public GroupMatchController(
        MatchQueryService matchQueryService,
        MmrAccessRequestResolver mmrAccessRequestResolver,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.matchQueryService = matchQueryService;
        this.mmrAccessRequestResolver = mmrAccessRequestResolver;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping("/{groupId}/matches/recent")
    public List<GroupRecentMatchResponse> getRecentMatches(
        @PathVariable Long groupId,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer offset,
        HttpServletRequest request
    ) {
        String requesterEmail = authenticatedRequestResolver.resolve(request).email();
        List<GroupRecentMatchResponse> response =
            matchQueryService.getRecentMatches(groupId, limit, offset, requesterEmail);
        if (mmrAccessRequestResolver.canViewMmr(request)) {
            return response;
        }

        return MmrMaskingMapper.maskRecentMatches(response);
    }

    /**
     * Full, database-paginated match history for a group, with an optional playedAt date range.
     * Restricted to super admins (see AdminKeyFilter's protected route registration) so that the
     * small caps on /matches/recent don't stand in the way of auditing the complete record.
     */
    @GetMapping("/{groupId}/matches/history")
    public GroupMatchPageResponse getMatchHistory(
        @PathVariable Long groupId,
        @RequestParam(name = "page", required = false, defaultValue = "0") int page,
        @RequestParam(name = "size", required = false) Integer size,
        @RequestParam(name = "fromDate", required = false) String fromDate,
        @RequestParam(name = "toDate", required = false) String toDate,
        HttpServletRequest request
    ) {
        LocalDate parsedFromDate = parseDate(fromDate, "fromDate");
        LocalDate parsedToDate = parseDate(toDate, "toDate");
        if (parsedFromDate != null && parsedToDate != null && parsedFromDate.isAfter(parsedToDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate must be before or equal to toDate");
        }

        String requesterEmail = authenticatedRequestResolver.resolve(request).email();
        GroupMatchPageResponse response = matchQueryService.getMatchHistoryPage(
            groupId,
            page,
            size,
            parsedFromDate,
            parsedToDate,
            requesterEmail
        );
        if (mmrAccessRequestResolver.canViewMmr(request)) {
            return response;
        }

        return new GroupMatchPageResponse(
            MmrMaskingMapper.maskRecentMatches(response.items()),
            response.page(),
            response.size(),
            response.totalElements(),
            response.totalPages(),
            response.first(),
            response.last()
        );
    }

    private LocalDate parseDate(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                parameterName + " must use yyyy-MM-dd format",
                exception
            );
        }
    }
}
