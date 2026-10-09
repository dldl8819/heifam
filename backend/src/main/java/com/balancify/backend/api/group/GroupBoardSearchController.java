package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.BoardSearchResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.BoardSearchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Search across the notices and the free board. It needs member access (ServiceAccessFilter) and
 * finds only what the member may read; BoardSearchService keeps admin-only notices to admins.
 */
@RestController
public class GroupBoardSearchController {

    private final BoardSearchService boardSearchService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public GroupBoardSearchController(
        BoardSearchService boardSearchService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.boardSearchService = boardSearchService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping("/api/groups/{groupId}/boards/search")
    public BoardSearchResponse search(
        @PathVariable Long groupId,
        @RequestParam(name = "q", defaultValue = "") String query,
        @RequestParam(defaultValue = "1") int page,
        HttpServletRequest request
    ) {
        String email = authenticatedRequestResolver.resolve(request).email();
        if (email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid Supabase bearer token is required");
        }
        try {
            return boardSearchService.search(groupId, email, query, page);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
