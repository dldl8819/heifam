package com.balancify.backend.service;

import com.balancify.backend.api.tournament.dto.TeamTournamentResponse;
import com.balancify.backend.api.tournament.dto.TournamentGameResponse;
import com.balancify.backend.api.tournament.dto.TournamentSeriesResponse;
import com.balancify.backend.api.tournament.dto.TournamentTeamResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.MatchSeries;
import com.balancify.backend.domain.MatchSeriesStatus;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.TeamTournament;
import com.balancify.backend.domain.TeamTournamentStatus;
import com.balancify.backend.domain.TournamentTeam;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.MatchSeriesRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.repository.TeamTournamentRepository;
import com.balancify.backend.repository.TournamentTeamRepository;
import com.balancify.backend.service.exception.MatchConflictException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, shows and cancels team tournaments; TournamentProgressService moves them along. */
@Service
public class TeamTournamentService {

    private final GroupRepository groupRepository;
    private final PlayerRepository playerRepository;
    private final TeamTournamentRepository teamTournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final MatchSeriesRepository matchSeriesRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final TournamentProgressService tournamentProgressService;
    private final OperationAuditLogService operationAuditLogService;

    public TeamTournamentService(
        GroupRepository groupRepository,
        PlayerRepository playerRepository,
        TeamTournamentRepository teamTournamentRepository,
        TournamentTeamRepository tournamentTeamRepository,
        MatchSeriesRepository matchSeriesRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        TournamentProgressService tournamentProgressService,
        OperationAuditLogService operationAuditLogService
    ) {
        this.groupRepository = groupRepository;
        this.playerRepository = playerRepository;
        this.teamTournamentRepository = teamTournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.matchSeriesRepository = matchSeriesRepository;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.tournamentProgressService = tournamentProgressService;
        this.operationAuditLogService = operationAuditLogService;
    }

    @Transactional
    public TeamTournamentResponse create(
        Long groupId,
        List<Long> playerIds,
        String actorEmail,
        String actorNickname,
        boolean showMmr
    ) {
        List<Long> requestedIds = normalizePlayerIds(playerIds);
        int teamCount = TournamentTeamFormation.teamCountFor(requestedIds.size());

        Group group = groupRepository.findByIdForUpdate(groupId)
            .orElseThrow(() -> new NoSuchElementException("Group not found: " + groupId));
        if (teamTournamentRepository.findFirstByGroup_IdAndStatusOrderByIdDesc(groupId, TeamTournamentStatus.IN_PROGRESS)
            .isPresent()) {
            throw new MatchConflictException("진행 중인 팀 토너먼트가 있습니다. 끝내거나 취소한 뒤 새로 만들어 주세요.");
        }

        List<Player> players = playerRepository.findByGroup_IdAndIdIn(groupId, requestedIds).stream()
            .filter(PlayerRosterPolicy::isOnRoster)
            .toList();
        if (players.size() != requestedIds.size()) {
            throw new IllegalArgumentException("그룹에 없는 선수가 있습니다.");
        }

        // Whoever sat out of the previous tournament plays in this one.
        Set<Long> mustPlayIds = teamTournamentRepository.findFirstByGroup_IdOrderByIdDesc(groupId)
            .map(previous -> (Set<Long>) new HashSet<>(previous.getWaitingPlayerIds()))
            .orElse(Set.of());
        List<TournamentTeamFormation.Candidate> candidates = players.stream()
            .map(player -> new TournamentTeamFormation.Candidate(
                player.getId(),
                player.getMmr() == null ? 0 : player.getMmr(),
                TournamentSeriesPlanner.capabilityOf(player.getRace())
            ))
            .toList();
        TournamentTeamFormation.Formation formation = TournamentTeamFormation.form(candidates, mustPlayIds);
        List<List<TournamentTeamFormation.Candidate>> orderedTeams = TournamentTeamFormation.bracketOrder(formation.teams());

        TeamTournament tournament = new TeamTournament();
        tournament.setGroup(group);
        tournament.setTeamCount(teamCount);
        Set<Long> waitingIds = new LinkedHashSet<>();
        formation.waiting().forEach(player -> waitingIds.add(player.playerId()));
        tournament.setWaitingPlayerIds(waitingIds);
        teamTournamentRepository.save(tournament);

        List<TournamentTeam> teams = new ArrayList<>();
        for (int index = 0; index < orderedTeams.size(); index++) {
            TournamentTeam team = new TournamentTeam();
            team.setTournament(tournament);
            team.setTeamNumber(index + 1);
            team.setMemberPlayerIds(new ArrayList<>(
                orderedTeams.get(index).stream().map(TournamentTeamFormation.Candidate::playerId).toList()
            ));
            teams.add(tournamentTeamRepository.save(team));
        }
        tournamentProgressService.openFirstRound(tournament, teams);

        operationAuditLogService.recordTournamentCreated(
            actorEmail,
            actorNickname,
            tournament.getId(),
            groupId,
            teamCount,
            waitingIds.size()
        );
        return toResponse(tournament, showMmr);
    }

    /** The group's latest tournament that was not cancelled, finished or not, or null. */
    @Transactional(readOnly = true)
    public TeamTournamentResponse findLatest(Long groupId, boolean showMmr) {
        return teamTournamentRepository.findFirstByGroup_IdAndStatusNotOrderByIdDesc(groupId, TeamTournamentStatus.CANCELLED)
            .map(tournament -> toResponse(tournament, showMmr))
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public TeamTournamentResponse get(Long groupId, Long tournamentId, boolean showMmr) {
        return toResponse(requireTournament(groupId, tournamentId), showMmr);
    }

    /** Games already played stay as ordinary matches; games set up but not played are removed. */
    @Transactional
    public TeamTournamentResponse cancel(
        Long groupId,
        Long tournamentId,
        String actorEmail,
        String actorNickname,
        boolean showMmr
    ) {
        requireTournament(groupId, tournamentId);
        TeamTournament tournament = teamTournamentRepository.findByIdForUpdate(tournamentId)
            .orElseThrow(() -> new NoSuchElementException("Tournament not found: " + tournamentId));
        if (tournament.getStatus() != TeamTournamentStatus.IN_PROGRESS) {
            throw new MatchConflictException("진행 중인 대회만 취소할 수 있습니다.");
        }
        tournament.setStatus(TeamTournamentStatus.CANCELLED);
        tournament.setFinishedAt(OffsetDateTime.now());
        tournamentProgressService.removeUnplayedGames(tournament);
        operationAuditLogService.recordTournamentCancelled(actorEmail, actorNickname, tournament.getId(), groupId);
        return toResponse(tournament, showMmr);
    }

    private TeamTournament requireTournament(Long groupId, Long tournamentId) {
        return teamTournamentRepository.findByIdAndGroup_Id(tournamentId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Tournament not found: " + tournamentId));
    }

    private List<Long> normalizePlayerIds(List<Long> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            throw new IllegalArgumentException("playerIds is required");
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long playerId : playerIds) {
            if (playerId == null || playerId <= 0) {
                throw new IllegalArgumentException("Player ID must be a positive number");
            }
            if (!unique.add(playerId)) {
                throw new IllegalArgumentException("playerIds must not contain duplicates");
            }
        }
        return List.copyOf(unique);
    }

    private TeamTournamentResponse toResponse(TeamTournament tournament, boolean showMmr) {
        List<TournamentTeam> teams = tournamentTeamRepository.findByTournament_IdOrderByTeamNumberAsc(tournament.getId());
        List<MatchSeries> seriesList = new ArrayList<>(matchSeriesRepository.findByTournament_IdOrderByIdAsc(tournament.getId()));
        seriesList.sort(Comparator
            .comparingInt((MatchSeries series) -> series.getRound().ordinal())
            .thenComparingInt(MatchSeries::getBracketSlot));

        Map<Long, List<Match>> gamesBySeries = new HashMap<>();
        if (!seriesList.isEmpty()) {
            List<Long> seriesIds = seriesList.stream().map(MatchSeries::getId).toList();
            for (Match game : matchRepository.findBySeriesIdInOrderBySeriesGameNumberAsc(seriesIds)) {
                gamesBySeries.computeIfAbsent(game.getSeriesId(), ignored -> new ArrayList<>()).add(game);
            }
        }
        List<Long> gameIds = gamesBySeries.values().stream().flatMap(List::stream).map(Match::getId).toList();
        Map<Long, Map<Long, MatchParticipant>> participantsByGame = new HashMap<>();
        if (!gameIds.isEmpty()) {
            for (MatchParticipant participant : matchParticipantRepository.findByMatchIdInWithPlayerAndMatch(gameIds)) {
                if (participant.getPlayer() == null) {
                    continue;
                }
                participantsByGame
                    .computeIfAbsent(participant.getMatch().getId(), ignored -> new HashMap<>())
                    .put(participant.getPlayer().getId(), participant);
            }
        }

        Set<Long> playerIds = new LinkedHashSet<>(tournament.getWaitingPlayerIds());
        teams.forEach(team -> playerIds.addAll(team.getMemberPlayerIds()));
        Map<Long, Player> players = new HashMap<>();
        playerRepository.findAllById(playerIds).forEach(player -> players.put(player.getId(), player));
        Map<Long, Integer> teamNumbers = new HashMap<>();
        teams.forEach(team -> teamNumbers.put(team.getId(), team.getTeamNumber()));

        List<TournamentTeamResponse> teamResponses = teams.stream()
            .map(team -> new TournamentTeamResponse(
                team.getId(),
                team.getTeamNumber(),
                team.getFinalRank(),
                showMmr ? totalMmr(team.getMemberPlayerIds(), players) : null,
                team.getMemberPlayerIds().stream().map(id -> SeriesGameViews.playerResponse(players.get(id), showMmr)).toList()
            ))
            .toList();
        List<TournamentSeriesResponse> seriesResponses = seriesList.stream()
            .map(series -> seriesResponse(
                series,
                gamesBySeries.getOrDefault(series.getId(), List.of()),
                participantsByGame,
                players,
                teamNumbers
            ))
            .toList();

        return new TeamTournamentResponse(
            tournament.getId(),
            tournament.getStatus().name(),
            tournament.getTeamCount(),
            tournament.getCreatedAt(),
            tournament.getFinishedAt(),
            tournament.getWaitingPlayerIds().stream().map(id -> SeriesGameViews.playerResponse(players.get(id), showMmr)).toList(),
            teamResponses,
            seriesResponses
        );
    }

    private TournamentSeriesResponse seriesResponse(
        MatchSeries series,
        List<Match> games,
        Map<Long, Map<Long, MatchParticipant>> participantsByGame,
        Map<Long, Player> players,
        Map<Long, Integer> teamNumbers
    ) {
        List<TournamentGameResponse> gameResponses = SeriesGameViews.games(
            series.plannedCompositions(),
            games,
            series.getHomeTeam().getMemberPlayerIds(),
            series.getAwayTeam().getMemberPlayerIds(),
            series.getStatus() == MatchSeriesStatus.COMPLETED,
            participantsByGame,
            players
        );

        return new TournamentSeriesResponse(
            series.getId(),
            series.getRound().name(),
            series.getBracketSlot(),
            series.getFormat().name(),
            series.getStatus().name(),
            teamNumbers.getOrDefault(series.getHomeTeam().getId(), 0),
            teamNumbers.getOrDefault(series.getAwayTeam().getId(), 0),
            series.getHomeWins(),
            series.getAwayWins(),
            series.getWinnerTeam() == null ? null : teamNumbers.get(series.getWinnerTeam().getId()),
            gameResponses
        );
    }

    private Integer totalMmr(List<Long> memberIds, Map<Long, Player> players) {
        int total = 0;
        for (Long id : memberIds) {
            Player player = players.get(id);
            total += player == null || player.getMmr() == null ? 0 : player.getMmr();
        }
        return total;
    }
}
