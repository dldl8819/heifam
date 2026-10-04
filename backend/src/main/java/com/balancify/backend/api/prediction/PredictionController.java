package com.balancify.backend.api.prediction;

import com.balancify.backend.api.prediction.dto.PredictionBoardResponse;
import com.balancify.backend.api.prediction.dto.PredictionMatchResponse;
import com.balancify.backend.api.prediction.dto.PredictionRequest;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.PredictionService;
import com.balancify.backend.service.exception.MatchConflictException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/groups/{groupId}/predictions")
public class PredictionController {

    private final PredictionService predictionService;
    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public PredictionController(
        PredictionService predictionService,
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.predictionService = predictionService;
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping
    public PredictionBoardResponse getBoard(@PathVariable Long groupId, HttpServletRequest request) {
        AuthenticatedRequestResolver.ResolvedRequestIdentity identity = requirePredictor(request);
        return predictionService.board(groupId, identity.email(), identity.userId());
    }

    @PutMapping("/{matchId}")
    public PredictionMatchResponse predict(
        @PathVariable Long groupId,
        @PathVariable Long matchId,
        @RequestBody PredictionRequest requestBody,
        HttpServletRequest request
    ) {
        AuthenticatedRequestResolver.ResolvedRequestIdentity identity = requirePredictor(request);
        if (requestBody == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        return handle(() -> predictionService.predict(
            groupId,
            matchId,
            identity.email(),
            identity.userId(),
            requestBody.team()
        ));
    }

    @PostMapping("/{matchId}/close")
    public void close(@PathVariable Long groupId, @PathVariable Long matchId, HttpServletRequest request) {
        String requestEmail = requirePredictor(request).email();
        if (!accessControlService.isAdminEmail(requestEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
        handle(() -> {
            predictionService.close(groupId, matchId, requestEmail, resolveNickname(requestEmail));
            return null;
        });
    }

    private AuthenticatedRequestResolver.ResolvedRequestIdentity requirePredictor(HttpServletRequest request) {
        AuthenticatedRequestResolver.ResolvedRequestIdentity identity = authenticatedRequestResolver.resolve(request);
        if (identity.email().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid Supabase bearer token is required");
        }
        if (!predictionService.canPredict(identity.email())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Predictions are not open to this account yet");
        }
        return identity;
    }

    private String resolveNickname(String email) {
        String nickname = accessControlService.resolveAccessProfile(email).nickname();
        return nickname == null || nickname.isBlank() ? null : nickname.trim();
    }

    private <T> T handle(Supplier<T> action) {
        try {
            return action.get();
        } catch (MatchConflictException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
