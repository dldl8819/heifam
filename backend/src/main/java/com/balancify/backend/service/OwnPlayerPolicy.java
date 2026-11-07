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
        for (MatchParticipant participant : participants) {
            if (isLinked(participant.getPlayer(), authUserId)) {
                return participant;
            }
        }
        String nickname = normalizeNickname(accountNickname);
        for (MatchParticipant participant : participants) {
            if (isNamed(participant.getPlayer(), nickname)) {
                return participant;
            }
        }
        return null;
    }

    /** Which of the players are the person's own: every one findOwn could pick in a match of theirs. */
    public static List<Player> findOwnPlayers(List<Player> players, String userId, String accountNickname) {
        UUID authUserId = parseUuid(userId);
        String nickname = normalizeNickname(accountNickname);
        return players.stream()
            .filter(player -> isLinked(player, authUserId) || isNamed(player, nickname))
            .toList();
    }

    private static boolean isLinked(Player player, UUID authUserId) {
        return player != null && authUserId != null && authUserId.equals(player.getAuthUserId());
    }

    private static boolean isNamed(Player player, String nickname) {
        return player != null
            && !nickname.isEmpty()
            && player.getNickname() != null
            && nickname.equalsIgnoreCase(player.getNickname().trim());
    }

    private static String normalizeNickname(String value) {
        return value == null ? "" : value.trim();
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
