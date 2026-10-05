package com.balancify.backend.service;

import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import java.util.List;
import java.util.UUID;

/**
 * Whether a signed-in person plays in a match. A player is theirs when linked to their login, or
 * named like their account's display nickname, which admins set in access control. Predictions
 * refuse picks on one's own match by it, and result confirmations pay only the match's players.
 */
public final class OwnPlayerPolicy {

    private OwnPlayerPolicy() {
    }

    /** The person's own participant in the match, or null; a player linked to the login comes first. */
    public static MatchParticipant findOwn(List<MatchParticipant> participants, String userId, String accountNickname) {
        if (participants == null || participants.isEmpty()) {
            return null;
        }
        UUID authUserId = parseUuid(userId);
        if (authUserId != null) {
            for (MatchParticipant participant : participants) {
                Player player = participant.getPlayer();
                if (player != null && authUserId.equals(player.getAuthUserId())) {
                    return participant;
                }
            }
        }
        String nickname = accountNickname == null ? "" : accountNickname.trim();
        if (nickname.isEmpty()) {
            return null;
        }
        for (MatchParticipant participant : participants) {
            Player player = participant.getPlayer();
            if (player != null && player.getNickname() != null && nickname.equalsIgnoreCase(player.getNickname().trim())) {
                return participant;
            }
        }
        return null;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
