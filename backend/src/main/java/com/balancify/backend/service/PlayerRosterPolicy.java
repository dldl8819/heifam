package com.balancify.backend.service;

import com.balancify.backend.domain.Player;

/**
 * Who is on the current roster. A dormant player (휴면) keeps their nickname on past results, but
 * the player list, the ranking and new balances and matches leave them out until an admin wakes
 * them. Hidden players (inactive, withdrawn, anonymized) are off the roster as before.
 */
public final class PlayerRosterPolicy {

    private PlayerRosterPolicy() {
    }

    /** A visible player an admin has set aside. */
    public static boolean isDormant(Player player) {
        return player != null && player.getDormantAt() != null && !PlayerIdentityPolicy.isIdentityHidden(player);
    }

    /** A visible player who is not dormant: one that lists and new matches may take. */
    public static boolean isOnRoster(Player player) {
        return !PlayerIdentityPolicy.isIdentityHidden(player) && player.getDormantAt() == null;
    }
}
