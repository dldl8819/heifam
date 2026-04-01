package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.GroupPlayerUpdateRequest;
import com.balancify.backend.service.PlayerAdminService;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/groups")
public class GroupPlayerAdminController {

    private final PlayerAdminService playerAdminService;

    public GroupPlayerAdminController(PlayerAdminService playerAdminService) {
        this.playerAdminService = playerAdminService;
    }

    @PatchMapping("/{groupId}/players/{playerId}")
    public void updatePlayer(
        @PathVariable Long groupId,
        @PathVariable Long playerId,
        @RequestBody GroupPlayerUpdateRequest request
    ) {
        try {
            playerAdminService.updatePlayer(groupId, playerId, request);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                illegalArgumentException.getMessage(),
                illegalArgumentException
            );
        } catch (NoSuchElementException noSuchElementException) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                noSuchElementException.getMessage(),
                noSuchElementException
            );
        }
    }

    @DeleteMapping("/{groupId}/players/{playerId}")
    public void deletePlayer(
        @PathVariable Long groupId,
        @PathVariable Long playerId
    ) {
        try {
            playerAdminService.deletePlayer(groupId, playerId);
        } catch (NoSuchElementException noSuchElementException) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                noSuchElementException.getMessage(),
                noSuchElementException
            );
        }
    }
}
