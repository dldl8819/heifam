package com.balancify.backend.api.points;

import com.balancify.backend.api.points.dto.PointAdjustmentRequest;
import com.balancify.backend.api.points.dto.PointAdjustmentResponse;
import com.balancify.backend.api.points.dto.PointRankingResponse;
import com.balancify.backend.api.points.dto.PointSummaryResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.PointService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PointController {

    private final PointService pointService;
    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public PointController(
        PointService pointService,
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.pointService = pointService;
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping("/api/points/me")
    public PointSummaryResponse getMyPoints(HttpServletRequest request) {
        String requestEmail = requireRequestEmail(request);
        requirePointAccess(requestEmail);
        return pointService.getSummary(requestEmail);
    }

    @GetMapping("/api/points/ranking")
    public PointRankingResponse getRanking(
        @RequestParam(name = "month", required = false) String month,
        HttpServletRequest request
    ) {
        requirePointAccess(requireRequestEmail(request));
        return pointService.getMonthlyRanking(parseMonth(month));
    }

    @PostMapping("/api/admin/points/adjustments")
    public PointAdjustmentResponse adjust(
        @RequestBody PointAdjustmentRequest requestBody,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        if (!accessControlService.isSuperAdminEmail(requestEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super admin role required");
        }
        if (requestBody == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        try {
            return pointService.adjust(
                requestEmail,
                resolveActorNickname(requestEmail),
                requestBody.email(),
                requestBody.amount(),
                requestBody.memo()
            );
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                illegalArgumentException.getMessage(),
                illegalArgumentException
            );
        }
    }

    private void requirePointAccess(String email) {
        if (!pointService.canUsePoints(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Points are not open to this account yet");
        }
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return pointService.currentMonth();
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must look like 2026-10", exception);
        }
    }

    private String requireRequestEmail(HttpServletRequest request) {
        String requestEmail = authenticatedRequestResolver.resolve(request).email();
        if (requestEmail.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid Supabase bearer token is required");
        }
        return requestEmail;
    }

    private String resolveActorNickname(String email) {
        String nickname = accessControlService.resolveAccessProfile(email).nickname();
        return nickname == null || nickname.isBlank() ? null : nickname.trim();
    }
}
