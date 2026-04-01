package com.balancify.backend.api.match;

import com.balancify.backend.api.MmrMaskingMapper;
import com.balancify.backend.api.match.dto.BalanceRequest;
import com.balancify.backend.api.match.dto.BalanceResponse;
import com.balancify.backend.api.match.dto.MultiBalanceRequest;
import com.balancify.backend.api.match.dto.MultiBalanceResponse;
import com.balancify.backend.security.AdminRequestResolver;
import com.balancify.backend.service.MultiMatchBalancingService;
import com.balancify.backend.service.TeamBalancingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/matches")
public class MatchBalanceController {

    private final TeamBalancingService teamBalancingService;
    private final MultiMatchBalancingService multiMatchBalancingService;
    private final AdminRequestResolver adminRequestResolver;

    public MatchBalanceController(
        TeamBalancingService teamBalancingService,
        MultiMatchBalancingService multiMatchBalancingService,
        AdminRequestResolver adminRequestResolver
    ) {
        this.teamBalancingService = teamBalancingService;
        this.multiMatchBalancingService = multiMatchBalancingService;
        this.adminRequestResolver = adminRequestResolver;
    }

    @PostMapping("/balance")
    public BalanceResponse balance(
        @RequestBody BalanceRequest request,
        HttpServletRequest httpServletRequest
    ) {
        try {
            BalanceResponse response = teamBalancingService.balance(request);
            if (adminRequestResolver.isAdminRequest(httpServletRequest)) {
                return response;
            }

            return MmrMaskingMapper.maskBalance(response);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PostMapping("/balance/multi")
    public MultiBalanceResponse balanceMulti(
        @RequestBody MultiBalanceRequest request,
        HttpServletRequest httpServletRequest
    ) {
        try {
            MultiBalanceResponse response = multiMatchBalancingService.balance(request);
            if (adminRequestResolver.isAdminRequest(httpServletRequest)) {
                return response;
            }

            return MmrMaskingMapper.maskMultiBalance(response);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }
}
