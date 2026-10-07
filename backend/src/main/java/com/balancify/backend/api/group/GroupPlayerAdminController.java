package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.GroupPlayerUpdateRequest;
import com.balancify.backend.api.group.dto.GroupPlayerMmrUpdateRequest;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.security.MmrAccessRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.PlayerAdminService;
import com.balancify.backend.service.exception.AccountDeletionException;
import com.balancify.backend.service.exception.PlayerEditForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import java.util.UUID;
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
    private final MmrAccessRequestResolver mmrAccessRequestResolver;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;
    private final AccessControlService accessControlService;

    public GroupPlayerAdminController(
        PlayerAdminService playerAdminService,
        MmrAccessRequestResolver mmrAccessRequestResolver,
        AuthenticatedRequestResolver authenticatedRequestResolver,
        AccessControlService accessControlService
    ) {
        this.playerAdminService = playerAdminService;
        this.mmrAccessRequestResolver = mmrAccessRequestResolver;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
        this.accessControlService = accessControlService;
    }

    @PatchMapping("/{groupId}/players/{playerId}")
    public void updatePlayer(
        @PathVariable Long groupId,
        @PathVariable Long playerId,
        @RequestBody GroupPlayerUpdateRequest request,
        HttpServletRequest httpRequest
    ) {
        if (hasTierChangeAcknowledgement(request) && !mmrAccessRequestResolver.canViewMmr(httpRequest)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "MMR access is required to acknowledge tier change"
            );
        }
        try {
            AuthenticatedRequestResolver.ResolvedRequestIdentity identity =
                authenticatedRequestResolver.resolve(httpRequest);
            playerAdminService.updatePlayer(
                groupId,
                playerId,
                request,
                identity.email(),
                resolveActorNickname(identity),
                resolveVerifiedAuthUserId(identity)
            );
        } catch (PlayerEditForbiddenException playerEditForbiddenException) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                playerEditForbiddenException.getMessage(),
                playerEditForbiddenException
            );
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
        } catch (AccountDeletionException accountDeletionException) {
            // Deactivating also closes the player's login account; say what stands in the way.
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                deactivationBlockedMessage(accountDeletionException.getReason()),
                accountDeletionException
            );
        }
    }

    static String deactivationBlockedMessage(AccountDeletionException.Reason reason) {
        return switch (reason) {
            case CONFIGURED_ACCESS_LIST ->
                "서버 환경변수(ADMIN_EMAILS·SUPER_ADMIN_EMAILS·ALLOWED_USER_EMAILS)에 등록된 계정이라 비활성화할 수 없습니다. 환경변수에서 먼저 빼 주세요.";
            case IDENTITY_UNRESOLVED ->
                "이 선수와 연결된 로그인 계정을 하나로 확인할 수 없어 비활성화하지 못했습니다.";
            case LINKED_ACCOUNT_WITHOUT_EMAIL ->
                "이 선수와 연결된 로그인 계정에 이메일 정보가 없어 비활성화하지 못했습니다.";
            case NICKNAME_SHARED_BY_PLAYERS ->
                "같은 닉네임을 쓰는 다른 선수 기록(비활성 선수 포함)이 있어 어느 계정인지 확인할 수 없습니다. 한쪽 닉네임을 바꾼 뒤 다시 시도해 주세요.";
            case NICKNAME_SHARED_BY_ACCOUNTS ->
                "권한 관리 목록에 이 닉네임을 쓰는 이메일이 여러 개라 어느 계정인지 확인할 수 없습니다. 권한 관리에서 중복을 정리한 뒤 다시 시도해 주세요.";
            case AUTH_UNAVAILABLE ->
                "로그인 계정 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.";
            case OTHER -> "연결된 계정 정보 때문에 비활성화하지 못했습니다.";
        };
    }

    @PatchMapping("/{groupId}/players/{playerId}/mmr")
    public void updatePlayerMmr(
        @PathVariable Long groupId,
        @PathVariable Long playerId,
        @RequestBody GroupPlayerMmrUpdateRequest request
    ) {
        try {
            playerAdminService.updatePlayerMmr(groupId, playerId, request);
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
        } catch (IllegalStateException illegalStateException) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                illegalStateException.getMessage(),
                illegalStateException
            );
        } catch (NoSuchElementException noSuchElementException) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                noSuchElementException.getMessage(),
                noSuchElementException
            );
        }
    }

    private boolean hasTierChangeAcknowledgement(GroupPlayerUpdateRequest request) {
        return request != null
            && request.tierChangeAcknowledgedTier() != null
            && !request.tierChangeAcknowledgedTier().isBlank();
    }

    private UUID resolveVerifiedAuthUserId(AuthenticatedRequestResolver.ResolvedRequestIdentity identity) {
        if (identity == null || !identity.jwtVerified() || identity.userId() == null || identity.userId().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(identity.userId().trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    // The name the audit log shows for the actor. It comes from access control, where admins set
    // it, never from the token: an account holder can put any nickname in their own token.
    private String resolveActorNickname(AuthenticatedRequestResolver.ResolvedRequestIdentity identity) {
        if (identity == null || identity.email().isBlank()) {
            return null;
        }

        String nickname = accessControlService.resolveAccessProfile(identity.email()).nickname();
        return nickname == null || nickname.isBlank() ? null : nickname.trim();
    }
}
