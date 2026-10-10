package com.balancify.backend.service;

import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerTierPolicy;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * A player whose tier is still to be set (배정 필요) may be balanced and play, but a balance match or a
 * multi-balance series with them in it is never recorded: their score would rest on a guess. An
 * admin sets their tier in 선수 관리 first. Manual entries, imports of past matches and tournament
 * games are not held to this.
 */
public final class UnassignedPlayerPolicy {

    private UnassignedPlayerPolicy() {
    }

    /** The nicknames of the players among these whose tier is still to be set, in name order. */
    public static List<String> unassignedNicknames(Collection<Player> players) {
        return players.stream()
            .filter(Objects::nonNull)
            .filter(player -> PlayerTierPolicy.isUnassigned(player.getTier()))
            .map(Player::getNickname)
            .distinct()
            .sorted()
            .toList();
    }

    public static String message(List<String> nicknames) {
        return "배정 필요 선수(" + String.join(", ", nicknames)
            + ")가 있어 결과를 입력할 수 없습니다. 선수 관리에서 티어를 먼저 정해 주세요.";
    }
}
