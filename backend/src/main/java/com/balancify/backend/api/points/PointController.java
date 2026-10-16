package com.balancify.backend.api.points;

import com.balancify.backend.api.points.dto.PointMenuVisitResponse;
import com.balancify.backend.api.points.dto.PointMonthlyHistoryResponse;
import com.balancify.backend.api.points.dto.PointPolicyResponse;
import com.balancify.backend.api.points.dto.PointRankingResponse;
import com.balancify.backend.api.points.dto.PointSummaryResponse;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AttendanceMenu;
import com.balancify.backend.service.PointService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PointController {

    private final PointService pointService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public PointController(
        PointService pointService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.pointService = pointService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping("/api/points/me")
    public PointSummaryResponse getMyPoints(HttpServletRequest request) {
        String requestEmail = requireRequestEmail(request);
        requirePointAccess(requestEmail);
        return pointService.getSummary(requestEmail);
    }

    @GetMapping("/api/points/policy")
    public PointPolicyResponse getPolicy(HttpServletRequest request) {
        requirePointAccess(requireRequestEmail(request));
        return pointService.getPolicy();
    }

    @GetMapping("/api/points/ranking")
    public PointRankingResponse getRanking(
        @RequestParam(name = "month", required = false) String month,
        HttpServletRequest request
    ) {
        requirePointAccess(requireRequestEmail(request));
        return pointService.getMonthlyRanking(parseMonth(month));
    }

    @GetMapping("/api/points/ranking/{accountId}")
    public PointMonthlyHistoryResponse getRankingHistory(
        @PathVariable Long accountId,
        @RequestParam(name = "month", required = false) String month,
        HttpServletRequest request
    ) {
        String requestEmail = requireRequestEmail(request);
        requirePointAccess(requestEmail);
        try {
            return pointService.getMonthlyHistory(accountId, parseMonth(month), requestEmail);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    /**
     * The pages call this once a menu a day when an attendance menu is opened. An account points are
     * not open to is answered that nothing was earned, as is a menu that already paid today.
     */
    @PostMapping("/api/points/menu-visits/{menu}")
    public PointMenuVisitResponse recordMenuVisit(@PathVariable String menu, HttpServletRequest request) {
        String requestEmail = requireRequestEmail(request);
        AttendanceMenu attendanceMenu = AttendanceMenu.fromKey(menu)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not an attendance menu"));
        return pointService.grantMenuVisitPoint(requestEmail, attendanceMenu);
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

}
