package com.balancify.backend.api.match;

import com.balancify.backend.api.match.dto.MatchResultRequest;
import com.balancify.backend.api.match.dto.MatchResultResponse;
import com.balancify.backend.service.MatchResultService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/matches")
public class MatchResultController {

    private static final String USER_EMAIL_HEADER = "X-USER-EMAIL";
    private static final String USER_NICKNAME_HEADER = "X-USER-NICKNAME";

    private final MatchResultService matchResultService;

    public MatchResultController(MatchResultService matchResultService) {
        this.matchResultService = matchResultService;
    }

    @PostMapping("/{id}/result")
    public MatchResultResponse processResult(
        @PathVariable("id") Long matchId,
        @RequestBody MatchResultRequest request,
        HttpServletRequest httpRequest
    ) {
        try {
            return matchResultService.processMatchResult(
                matchId,
                request,
                extractRequestEmail(httpRequest),
                extractRequestNickname(httpRequest)
            );
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PatchMapping("/{id}/result")
    public MatchResultResponse updateResult(
        @PathVariable("id") Long matchId,
        @RequestBody MatchResultRequest request,
        HttpServletRequest httpRequest
    ) {
        try {
            return matchResultService.processMatchResult(
                matchId,
                request,
                extractRequestEmail(httpRequest),
                extractRequestNickname(httpRequest)
            );
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public void deleteMatch(@PathVariable("id") Long matchId) {
        try {
            matchResultService.deleteMatch(matchId);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    private String extractRequestEmail(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader(USER_EMAIL_HEADER);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String extractRequestNickname(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader(USER_NICKNAME_HEADER);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
