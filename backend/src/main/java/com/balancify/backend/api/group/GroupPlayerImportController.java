package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.GroupPlayerImportRequest;
import com.balancify.backend.api.group.dto.GroupPlayerImportResponse;
import com.balancify.backend.service.PlayerImportService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupPlayerImportController {

    private final PlayerImportService playerImportService;

    public GroupPlayerImportController(PlayerImportService playerImportService) {
        this.playerImportService = playerImportService;
    }

    @PostMapping("/{groupId}/players/import")
    public GroupPlayerImportResponse importPlayers(
        @PathVariable Long groupId,
        @RequestBody GroupPlayerImportRequest request
    ) {
        return playerImportService.importPlayers(groupId, request);
    }
}
