package com.balancify.backend.api.admin;

import com.balancify.backend.api.admin.dto.RatingRecalculationRequest;
import com.balancify.backend.api.admin.dto.RatingRecalculationResponse;
import com.balancify.backend.service.RatingRecalculationService;
import com.balancify.backend.service.exception.MatchConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/rating")
public class AdminRatingController {

    private final RatingRecalculationService ratingRecalculationService;

    public AdminRatingController(RatingRecalculationService ratingRecalculationService) {
        this.ratingRecalculationService = ratingRecalculationService;
    }

    @PostMapping("/recalculate")
    public RatingRecalculationResponse recalculate(
        @RequestBody(required = false) RatingRecalculationRequest request
    ) {
        try {
            return ratingRecalculationService.recalculate(request);
        } catch (MatchConflictException | ObjectOptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }
}
