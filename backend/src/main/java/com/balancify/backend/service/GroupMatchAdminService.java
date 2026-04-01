package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.CreateGroupMatchRequest;
import com.balancify.backend.api.group.dto.CreateGroupMatchResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupMatchAdminService {

    private static final String TEAM_HOME = "HOME";
    private static final String TEAM_AWAY = "AWAY";

    private final GroupRepository groupRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;

    public GroupMatchAdminService(
        GroupRepository groupRepository,
        PlayerRepository playerRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository
    ) {
        this.groupRepository = groupRepository;
        this.playerRepository = playerRepository;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
    }

    @Transactional
    public CreateGroupMatchResponse createMatch(Long groupId, CreateGroupMatchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        List<Long> homePlayerIds = normalizePlayerIds(request.homePlayerIds());
        List<Long> awayPlayerIds = normalizePlayerIds(request.awayPlayerIds());

        if (homePlayerIds.size() != 3 || awayPlayerIds.size() != 3) {
            throw new IllegalArgumentException("Exactly 3 HOME and 3 AWAY players are required");
        }

        Set<Long> allIds = new LinkedHashSet<>();
        allIds.addAll(homePlayerIds);
        allIds.addAll(awayPlayerIds);
        if (allIds.size() != 6) {
            throw new IllegalArgumentException("Players must be unique across both teams");
        }

        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new NoSuchElementException("Group not found: " + groupId));

        List<Player> players = playerRepository.findByGroup_IdAndIdIn(groupId, new ArrayList<>(allIds));
        if (players.size() != 6) {
            throw new IllegalArgumentException("All players must belong to the group");
        }

        Map<Long, Player> playersById = players
            .stream()
            .collect(Collectors.toMap(Player::getId, Function.identity()));

        Match match = new Match();
        match.setGroup(group);
        match.setPlayedAt(OffsetDateTime.now());
        Match savedMatch = matchRepository.save(match);

        List<MatchParticipant> participants = new ArrayList<>();
        for (Long playerId : homePlayerIds) {
            participants.add(createParticipant(savedMatch, playersById.get(playerId), TEAM_HOME));
        }
        for (Long playerId : awayPlayerIds) {
            participants.add(createParticipant(savedMatch, playersById.get(playerId), TEAM_AWAY));
        }

        matchParticipantRepository.saveAll(participants);

        return new CreateGroupMatchResponse(savedMatch.getId());
    }

    private MatchParticipant createParticipant(Match match, Player player, String team) {
        if (player == null) {
            throw new IllegalArgumentException("Player not found in group");
        }

        MatchParticipant participant = new MatchParticipant();
        participant.setMatch(match);
        participant.setPlayer(player);
        participant.setTeam(team);
        participant.setRace(player.getRace());
        participant.setMmrBefore(player.getMmr());
        participant.setMmrAfter(player.getMmr());
        participant.setMmrDelta(0);
        return participant;
    }

    private List<Long> normalizePlayerIds(List<Long> playerIds) {
        if (playerIds == null) {
            return List.of();
        }

        List<Long> normalized = new ArrayList<>();
        for (Long playerId : playerIds) {
            if (playerId == null || playerId <= 0) {
                throw new IllegalArgumentException("Player ID must be a positive number");
            }
            normalized.add(playerId);
        }
        return normalized;
    }
}
