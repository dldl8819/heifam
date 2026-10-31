package com.balancify.backend.service;

import com.balancify.backend.config.PointProperties;
import com.balancify.backend.domain.CaptainDraft;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.AccountPersonalDataRepository;
import com.balancify.backend.security.SupabaseAuthAdminClient;
import com.balancify.backend.service.exception.AccountDeletionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Points for captaining a regular draft (정기 감전). Each captain earns them once a draft as soon as
 * the draft is being played, that is once a set has a result, and loses them when the draft record
 * is deleted. A draft is started by any member, so starting one earns nothing by itself.
 *
 * A captain is a player, and the points go to the account that player belongs to, as
 * OwnPlayerPolicy reads it: the login the player is linked to, or else the one account whose access
 * nickname is the player's. A captain no single account can be found for earns nothing, and is
 * looked up again with the draft's next save.
 */
@Component
public class CaptainDraftPoints {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaptainDraftPoints.class);

    private final PointService pointService;
    private final PointProperties pointProperties;
    private final AccountPersonalDataRepository accountPersonalDataRepository;
    private final SupabaseAuthAdminClient supabaseAuthAdminClient;

    public CaptainDraftPoints(
        PointService pointService,
        PointProperties pointProperties,
        AccountPersonalDataRepository accountPersonalDataRepository,
        SupabaseAuthAdminClient supabaseAuthAdminClient
    ) {
        this.pointService = pointService;
        this.pointProperties = pointProperties;
        this.accountPersonalDataRepository = accountPersonalDataRepository;
        this.supabaseAuthAdminClient = supabaseAuthAdminClient;
    }

    /**
     * Pays the captains of a draft being played who were not paid for it yet. Called with every save
     * that leaves a set result in; a captain already paid costs one small lookup and nothing else.
     */
    public void grantWhilePlayed(CaptainDraft draft) {
        if (draft == null || draft.getId() == null || pointProperties.getCaptainDraft() <= 0) {
            return;
        }
        for (Player captain : captains(draft)) {
            if (PlayerIdentityPolicy.isIdentityHidden(captain)
                || pointService.hasCaptainDraftPoint(draft.getId(), captain.getId())) {
                continue;
            }
            accountEmail(captain).ifPresent(email ->
                pointService.grantCaptainDraftPoint(email, draft.getId(), captain.getId())
            );
        }
    }

    /** Takes the captains' points back, for a draft record being deleted. */
    public void takeBack(CaptainDraft draft) {
        if (draft != null && draft.getId() != null) {
            pointService.reverseCaptainDraftPoints(
                draft.getId(),
                captains(draft).stream().map(Player::getId).toList()
            );
        }
    }

    private static List<Player> captains(CaptainDraft draft) {
        List<Player> captains = new ArrayList<>();
        for (Player captain : new Player[] { draft.getHomeCaptain(), draft.getAwayCaptain() }) {
            if (captain != null && captain.getId() != null) {
                captains.add(captain);
            }
        }
        return captains;
    }

    // public.users only holds accounts the optional profile sync wrote, so Supabase Auth is asked otherwise.
    Optional<String> accountEmail(Player player) {
        UUID linkedAuthUserId = player.getAuthUserId();
        if (linkedAuthUserId != null) {
            Optional<String> stored = accountPersonalDataRepository.findAccountEmail(linkedAuthUserId);
            if (stored.isPresent()) {
                return stored;
            }
            try {
                return supabaseAuthAdminClient.findUserEmail(linkedAuthUserId);
            } catch (AccountDeletionException unavailable) {
                LOGGER.warn("Captain draft points: the linked account of player {} could not be read", player.getId());
                return Optional.empty();
            }
        }
        List<String> emails = accountPersonalDataRepository.findAccountIdentitiesByNickname(player.getNickname())
            .stream()
            .map(AccountPersonalDataRepository.NicknameAccountCandidate::normalizedEmail)
            .filter(email -> email != null && !email.isBlank())
            .distinct()
            .toList();
        return emails.size() == 1 ? Optional.of(emails.get(0)) : Optional.empty();
    }
}
