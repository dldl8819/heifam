package com.balancify.backend.api.match;

import com.balancify.backend.api.match.dto.MatchImportResponse;
import com.balancify.backend.api.match.dto.MatchImportRowRequest;
import com.balancify.backend.service.MatchImportService;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
public class MatchImportController {

    private final MatchImportService matchImportService;

    public MatchImportController(MatchImportService matchImportService) {
        this.matchImportService = matchImportService;
    }

    @PostMapping("/import")
    public MatchImportResponse importMatches(@RequestBody(required = false) List<MatchImportRowRequest> request) {
        return matchImportService.importMatches(request);
    }
}

