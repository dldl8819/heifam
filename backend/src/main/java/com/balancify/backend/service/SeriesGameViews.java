package com.balancify.backend.service;

import com.balancify.backend.api.tournament.dto.TournamentGamePlayerResponse;
import com.balancify.backend.api.tournament.dto.TournamentGameResponse;
import com.balancify.backend.api.tournament.dto.TournamentPlayerResponse;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** How the games of a series and their players are shown, for tournament and multi-balance series alike. */
final class SeriesGameViews {

    private SeriesGameViews() {
    }

    /**
     * Every planned game: a game set up as a match shows its recorded races (NEXT or PLAYED), a game
     * not set up yet shows the planned races (UPCOMING, or SKIPPED once the series is decided).
     */
    static List<TournamentGameResponse> games(
        List<String> plannedCompositions,
        List<Match> games,
        List<Long> homeIds,
        List<Long> awayIds,
        boolean decided,
        Map<Long, Map<Long, MatchParticipant>> participantsByGame,
        Map<Long, Player> players
    ) {
        Map<Integer, Match> gamesByNumber = new HashMap<>();
        games.forEach(game -> gamesByNumber.put(game.getSeriesGameNumber(), game));

        List<TournamentGameResponse> responses = new ArrayList<>();
        for (int number = 1; number <= plannedCompositions.size(); number++) {
            Match game = gamesByNumber.get(number);
            if (game != null) {
                Map<Long, MatchParticipant> participants = participantsByGame.getOrDefault(game.getId(), Map.of());
                responses.add(new TournamentGameResponse(
                    number,
                    game.getRaceComposition(),
                    game.getWinningTeam() == null ? "NEXT" : "PLAYED",
                    game.getId(),
                    game.getWinningTeam(),
                    recordedPlayers(homeIds, participants, players),
                    recordedPlayers(awayIds, participants, players)
                ));
                continue;
            }
            String composition = plannedCompositions.get(number - 1);
            responses.add(new TournamentGameResponse(
                number,
                composition,
                decided ? "SKIPPED" : "UPCOMING",
                null,
                null,
                plannedPlayers(homeIds, composition, players),
                plannedPlayers(awayIds, composition, players)
            ));
        }
        return responses;
    }

    static TournamentPlayerResponse playerResponse(Player player, boolean showMmr) {
        boolean hidden = PlayerIdentityPolicy.isIdentityHidden(player);
        return new TournamentPlayerResponse(
            PlayerIdentityPolicy.responsePlayerId(player),
            PlayerIdentityPolicy.responseNickname(player),
            hidden ? null : TournamentSeriesPlanner.capabilityOf(player.getRace()),
            showMmr && !hidden && player.getMmr() != null ? player.getMmr() : null
        );
    }

    private static List<TournamentGamePlayerResponse> recordedPlayers(
        List<Long> memberIds,
        Map<Long, MatchParticipant> participants,
        Map<Long, Player> players
    ) {
        return memberIds.stream()
            .map(id -> {
                MatchParticipant participant = participants.get(id);
                String race = participant == null || participant.getAssignedRace() == null
                    ? null
                    : participant.getAssignedRace().trim().toUpperCase(Locale.ROOT);
                return gamePlayer(players.get(id), race);
            })
            .toList();
    }

    private static List<TournamentGamePlayerResponse> plannedPlayers(
        List<Long> memberIds,
        String composition,
        Map<Long, Player> players
    ) {
        List<String> capabilities = memberIds.stream()
            .map(id -> TournamentSeriesPlanner.capabilityOf(players.get(id) == null ? null : players.get(id).getRace()))
            .toList();
        List<String> races = TournamentSeriesPlanner.assignRaces(capabilities, composition);
        List<TournamentGamePlayerResponse> planned = new ArrayList<>();
        for (int index = 0; index < memberIds.size(); index++) {
            planned.add(gamePlayer(players.get(memberIds.get(index)), races == null ? null : races.get(index)));
        }
        return planned;
    }

    private static TournamentGamePlayerResponse gamePlayer(Player player, String assignedRace) {
        return new TournamentGamePlayerResponse(
            PlayerIdentityPolicy.responsePlayerId(player),
            PlayerIdentityPolicy.responseNickname(player),
            assignedRace
        );
    }
}
