package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.CreateGroupMatchRequest;
import com.balancify.backend.api.group.dto.CreateGroupMatchResponse;
import com.balancify.backend.service.GroupMatchAdminService;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/groups")
public class GroupMatchAdminController {

    private final GroupMatchAdminService groupMatchAdminService;

    public GroupMatchAdminController(GroupMatchAdminService groupMatchAdminService) {
        this.groupMatchAdminService = groupMatchAdminService;
    }

    @PostMapping("/{groupId}/matches")
    public CreateGroupMatchResponse createGroupMatch(
        @PathVariable Long groupId,
        @RequestBody CreateGroupMatchRequest request
    ) {
        try {
            return groupMatchAdminService.createMatch(groupId, request);
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
}
