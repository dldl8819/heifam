package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.CaptainDraftCreateRequest;
import com.balancify.backend.api.group.dto.CaptainDraftEntriesUpdateRequest;
import com.balancify.backend.api.group.dto.CaptainDraftEntryPlayerResponse;
import com.balancify.backend.api.group.dto.CaptainDraftEntryResponse;
import com.balancify.backend.api.group.dto.CaptainDraftEntryUpdateItemRequest;
import com.balancify.backend.api.group.dto.CaptainDraftParticipantResponse;
import com.balancify.backend.api.group.dto.CaptainDraftPickLogResponse;
import com.balancify.backend.api.group.dto.CaptainDraftPickRequest;
import com.balancify.backend.api.group.dto.CaptainDraftResponse;
import com.balancify.backend.api.group.dto.CaptainDraftUnpickRequest;
import com.balancify.backend.domain.CaptainDraft;
import com.balancify.backend.domain.CaptainDraftEntry;
import com.balancify.backend.domain.CaptainDraftParticipant;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.CaptainDraftEntryRepository;
import com.balancify.backend.repository.CaptainDraftParticipantRepository;
import com.balancify.backend.repository.CaptainDraftRepository;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaptainDraftService {

    private static final String TEAM_HOME = "HOME";
    private static final String TEAM_AWAY = "AWAY";
    private static final String TEAM_UNASSIGNED = "UNASSIGNED";
    private static final String STATUS_DRAFTING = "DRAFTING";
    private static final String STATUS_READY = "READY";
    // Rounds take these compositions in turn unless the host picks others.
    private static final List<String> DEFAULT_ROUND_CODES = List.of("PPP", "PPT", "PPZ", "PTZ");
    private static final String RACE_ORDER = "PTZR";
    // The 2:2 game each usual round composition is played with, as the clan plays them.
    private static final Map<String, String> TWO_A_SIDE = Map.of("PPP", "PP", "PPT", "PT", "PPZ", "PZ", "PTZ", "PR");
    private static final int DEFAULT_ROUND_COUNT = 4;
    private static final int MAX_ROUND_COUNT = 7;
    private static final int MAX_SETS_PER_ROUND = 10;
    private static final int MAX_REQUIRED_TIER_LENGTH = 30;

    private final GroupRepository groupRepository;
    private final PlayerRepository playerRepository;
    private final CaptainDraftRepository captainDraftRepository;
    private final CaptainDraftParticipantRepository captainDraftParticipantRepository;
    private final CaptainDraftEntryRepository captainDraftEntryRepository;

    public CaptainDraftService(
        GroupRepository groupRepository,
        PlayerRepository playerRepository,
        CaptainDraftRepository captainDraftRepository,
        CaptainDraftParticipantRepository captainDraftParticipantRepository,
        CaptainDraftEntryRepository captainDraftEntryRepository
    ) {
        this.groupRepository = groupRepository;
        this.playerRepository = playerRepository;
        this.captainDraftRepository = captainDraftRepository;
        this.captainDraftParticipantRepository = captainDraftParticipantRepository;
        this.captainDraftEntryRepository = captainDraftEntryRepository;
    }

    @Transactional
    public CaptainDraftResponse createDraft(Long groupId, CaptainDraftCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        List<Long> participantPlayerIds = normalizeDistinctPlayerIds(request.participantPlayerIds());
        List<Long> captainPlayerIds = normalizeDistinctPlayerIds(request.captainPlayerIds());

        if (participantPlayerIds.size() < 8) {
            throw new IllegalArgumentException("At least 8 participants are required");
        }
        if (participantPlayerIds.size() % 2 != 0) {
            throw new IllegalArgumentException("Participant count must be an even number");
        }
        if (captainPlayerIds.size() != 2) {
            throw new IllegalArgumentException("Exactly 2 captains are required");
        }
        if (!participantPlayerIds.containsAll(captainPlayerIds)) {
            throw new IllegalArgumentException("Captains must be included in participant list");
        }

        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new NoSuchElementException("Group not found: " + groupId));

        List<Player> players = playerRepository.findByGroup_IdAndIdIn(groupId, participantPlayerIds)
            .stream()
            .filter(PlayerRosterPolicy::isOnRoster)
            .toList();
        if (players.size() != participantPlayerIds.size()) {
            throw new IllegalArgumentException("All participants must belong to the group");
        }

        Map<Long, Player> playersById = players
            .stream()
            .collect(Collectors.toMap(Player::getId, player -> player));

        Player homeCaptain = playersById.get(captainPlayerIds.get(0));
        Player awayCaptain = playersById.get(captainPlayerIds.get(1));
        if (homeCaptain == null || awayCaptain == null) {
            throw new IllegalArgumentException("Invalid captain player IDs");
        }

        CaptainDraft draft = new CaptainDraft();
        draft.setGroup(group);
        draft.setTitle(resolveTitle(request.title()));
        draft.setStatus(STATUS_DRAFTING);
        int playersPerTeam = participantPlayerIds.size() / 2;
        List<Integer> teamSizes = resolveSetSizes(request.setSizes(), request.setsPerRound(), playersPerTeam);
        int roundCount = resolveRoundCount(request.roundCount());
        draft.setSetsPerRound(teamSizes.size());
        draft.setCurrentTurnTeam(TEAM_HOME);
        draft.setHomeCaptain(homeCaptain);
        draft.setAwayCaptain(awayCaptain);
        CaptainDraft savedDraft = captainDraftRepository.save(draft);

        List<CaptainDraftParticipant> participants = new ArrayList<>();
        for (Long playerId : participantPlayerIds) {
            Player participantPlayer = playersById.get(playerId);
            CaptainDraftParticipant participant = new CaptainDraftParticipant();
            participant.setDraft(savedDraft);
            participant.setPlayer(participantPlayer);

            if (playerId.equals(homeCaptain.getId())) {
                participant.setCaptain(true);
                participant.setTeam(TEAM_HOME);
            } else if (playerId.equals(awayCaptain.getId())) {
                participant.setCaptain(true);
                participant.setTeam(TEAM_AWAY);
            } else {
                participant.setCaptain(false);
                participant.setTeam(null);
            }
            participants.add(participant);
        }
        captainDraftParticipantRepository.saveAll(participants);

        List<CaptainDraftEntry> entries = new ArrayList<>();
        for (int roundIndex = 0; roundIndex < roundCount; roundIndex++) {
            int roundNumber = roundIndex + 1;
            String roundCode = DEFAULT_ROUND_CODES.get(roundIndex % DEFAULT_ROUND_CODES.size());
            for (int setNumber = 1; setNumber <= teamSizes.size(); setNumber++) {
                int teamSize = teamSizes.get(setNumber - 1);
                CaptainDraftEntry entry = new CaptainDraftEntry();
                entry.setDraft(savedDraft);
                entry.setRoundNumber(roundNumber);
                entry.setRoundCode(setComposition(roundCode, teamSize));
                entry.setSetNumber(setNumber);
                entry.setTeamSize(teamSize);
                entries.add(entry);
            }
        }
        captainDraftEntryRepository.saveAll(entries);

        return toResponse(savedDraft, participants, entries);
    }

    @Transactional(readOnly = true)
    public CaptainDraftResponse getDraft(Long groupId, Long draftId) {
        CaptainDraft draft = loadDraft(groupId, draftId);
        List<CaptainDraftParticipant> participants =
            captainDraftParticipantRepository.findByDraftIdWithPlayer(draft.getId());
        List<CaptainDraftEntry> entries = captainDraftEntryRepository.findByDraftIdWithPlayers(draft.getId());
        return toResponse(draft, participants, entries);
    }

    @Transactional(readOnly = true)
    public CaptainDraftResponse getLatestDraft(Long groupId) {
        CaptainDraft draft = captainDraftRepository.findTopByGroup_IdOrderByCreatedAtDescIdDesc(groupId)
            .orElseThrow(() -> new NoSuchElementException("Draft not found for group: " + groupId));

        List<CaptainDraftParticipant> participants =
            captainDraftParticipantRepository.findByDraftIdWithPlayer(draft.getId());
        List<CaptainDraftEntry> entries = captainDraftEntryRepository.findByDraftIdWithPlayers(draft.getId());
        return toResponse(draft, participants, entries);
    }

    @Transactional
    public CaptainDraftResponse pickPlayer(Long groupId, Long draftId, CaptainDraftPickRequest request) {
        if (request == null || request.captainPlayerId() == null || request.pickedPlayerId() == null) {
            throw new IllegalArgumentException("captainPlayerId and pickedPlayerId are required");
        }

        CaptainDraft draft = loadDraft(groupId, draftId);
        if (!STATUS_DRAFTING.equalsIgnoreCase(safeTrim(draft.getStatus()))) {
            throw new IllegalArgumentException("Draft picks are already completed");
        }

        List<CaptainDraftParticipant> participants =
            captainDraftParticipantRepository.findByDraftIdWithPlayer(draft.getId());
        requireActiveDraftParticipants(participants);
        Map<Long, CaptainDraftParticipant> participantsByPlayerId = participants
            .stream()
            .collect(Collectors.toMap(participant -> participant.getPlayer().getId(), participant -> participant));

        CaptainDraftParticipant captain = participantsByPlayerId.get(request.captainPlayerId());
        if (captain == null || !captain.isCaptain()) {
            throw new IllegalArgumentException("Invalid captain player ID");
        }

        // The host enters what the captains call out, and a captain may take more than one player
        // in a row, so picks are not held to alternating turns.
        String captainTeam = normalizeTeam(captain.getTeam());

        CaptainDraftParticipant pickedParticipant = participantsByPlayerId.get(request.pickedPlayerId());
        if (pickedParticipant == null || pickedParticipant.isCaptain()) {
            throw new IllegalArgumentException("Picked player must be a non-captain participant");
        }
        if (!TEAM_UNASSIGNED.equals(normalizeTeam(pickedParticipant.getTeam()))) {
            throw new IllegalArgumentException("Picked player is already assigned");
        }

        pickedParticipant.setTeam(captainTeam);
        pickedParticipant.setPickOrder(nextPickOrder(participants));
        captainDraftParticipantRepository.save(pickedParticipant);

        boolean hasUnassigned = participants
            .stream()
            .anyMatch(participant ->
                !participant.isCaptain() && TEAM_UNASSIGNED.equals(normalizeTeam(participant.getTeam()))
            );

        if (hasUnassigned) {
            draft.setCurrentTurnTeam(TEAM_HOME.equals(captainTeam) ? TEAM_AWAY : TEAM_HOME);
        } else {
            draft.setStatus(STATUS_READY);
            draft.setCurrentTurnTeam(null);
        }
        captainDraftRepository.save(draft);

        List<CaptainDraftEntry> entries = captainDraftEntryRepository.findByDraftIdWithPlayers(draft.getId());
        return toResponse(draft, participants, entries);
    }

    /**
     * Takes a picked player back out of their team, for a pick entered by mistake. Entries that
     * named them lose them and their result, and a finished draft is open again.
     */
    @Transactional
    public CaptainDraftResponse unpickPlayer(Long groupId, Long draftId, CaptainDraftUnpickRequest request) {
        if (request == null || request.playerId() == null) {
            throw new IllegalArgumentException("playerId is required");
        }

        CaptainDraft draft = loadDraft(groupId, draftId);
        List<CaptainDraftParticipant> participants =
            captainDraftParticipantRepository.findByDraftIdWithPlayer(draft.getId());
        requireActiveDraftParticipants(participants);
        CaptainDraftParticipant participant = participants
            .stream()
            .filter(candidate -> request.playerId().equals(candidate.getPlayer().getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Player is not a participant of this draft"));
        if (participant.isCaptain()) {
            throw new IllegalArgumentException("A captain cannot be taken out of their team");
        }
        String team = normalizeTeam(participant.getTeam());
        if (TEAM_UNASSIGNED.equals(team)) {
            throw new IllegalArgumentException("Player is not picked");
        }

        participant.setTeam(null);
        participant.setPickOrder(null);
        captainDraftParticipantRepository.save(participant);

        List<CaptainDraftEntry> entries = captainDraftEntryRepository.findByDraftIdWithPlayers(draft.getId());
        for (CaptainDraftEntry entry : entries) {
            List<Player> side = entry.sidePlayers(team);
            boolean named = false;
            for (int index = 0; index < side.size(); index++) {
                if (isPlayer(side.get(index), request.playerId())) {
                    side.set(index, null);
                    named = true;
                }
            }
            if (named) {
                entry.setSidePlayers(team, side);
                entry.setWinnerTeam(null);
            }
        }
        captainDraftEntryRepository.saveAll(entries);

        if (STATUS_READY.equalsIgnoreCase(safeTrim(draft.getStatus()))) {
            draft.setStatus(STATUS_DRAFTING);
            draft.setCurrentTurnTeam(team);
            captainDraftRepository.save(draft);
        }
        return toResponse(draft, participants, entries);
    }

    /**
     * Saves the sets as the host fills them in, each with its race composition. Each side of a set takes
     * up to its team size of its own team's players, a player plays at most one set a round, and a
     * set gets a winner only with both sides full.
     */
    @Transactional
    public CaptainDraftResponse updateEntries(
        Long groupId,
        Long draftId,
        CaptainDraftEntriesUpdateRequest request
    ) {
        if (request == null || request.entries() == null || request.entries().isEmpty()) {
            throw new IllegalArgumentException("entries are required");
        }

        CaptainDraft draft = loadDraft(groupId, draftId);
        List<CaptainDraftParticipant> participants =
            captainDraftParticipantRepository.findByDraftIdWithPlayer(draft.getId());
        requireActiveDraftParticipants(participants);

        Map<Long, CaptainDraftParticipant> participantsByPlayerId = participants
            .stream()
            .collect(Collectors.toMap(participant -> participant.getPlayer().getId(), participant -> participant));
        Map<String, Set<Long>> teamPlayerIds = Map.of(
            TEAM_HOME, teamPlayerIds(participants, TEAM_HOME),
            TEAM_AWAY, teamPlayerIds(participants, TEAM_AWAY)
        );

        List<CaptainDraftEntry> entries = captainDraftEntryRepository.findByDraftIdWithPlayers(draft.getId());
        Map<String, CaptainDraftEntry> entriesByKey = new HashMap<>();
        for (CaptainDraftEntry entry : entries) {
            entriesByKey.put(entryKey(entry.getRoundNumber(), entry.getSetNumber()), entry);
        }

        for (CaptainDraftEntryUpdateItemRequest updateItem : request.entries()) {
            if (updateItem == null) {
                continue;
            }
            CaptainDraftEntry targetEntry = entriesByKey.get(entryKey(updateItem.roundNumber(), updateItem.setNumber()));
            if (targetEntry == null) {
                throw new IllegalArgumentException("Invalid round/set combination");
            }
            // A page from before team sets sends no player lists, and must not empty the sets.
            if (updateItem.homePlayerIds() == null || updateItem.awayPlayerIds() == null) {
                throw new IllegalArgumentException("Each set needs both sides' player lists; reload the page");
            }

            for (String team : List.of(TEAM_HOME, TEAM_AWAY)) {
                targetEntry.setSidePlayers(team, sidePlayers(
                    TEAM_HOME.equals(team) ? updateItem.homePlayerIds() : updateItem.awayPlayerIds(),
                    targetEntry.getTeamSize(),
                    teamPlayerIds.get(team),
                    participantsByPlayerId
                ));
            }
            targetEntry.setRequiredTier(normalizeRequiredTier(updateItem.requiredTier()));
            if (updateItem.raceCode() != null) {
                targetEntry.setRoundCode(normalizeRaceCode(updateItem.raceCode()));
            }

            String winnerTeam = normalizeWinnerTeam(updateItem.winnerTeam());
            if (winnerTeam != null && (!isSideFull(targetEntry, TEAM_HOME) || !isSideFull(targetEntry, TEAM_AWAY))) {
                throw new IllegalArgumentException("Winner team requires both home and away players");
            }
            targetEntry.setWinnerTeam(winnerTeam);
        }

        requireOneSetPerRound(entries);
        captainDraftEntryRepository.saveAll(entries);
        return toResponse(draft, participants, entries);
    }

    /**
     * The team size of each set of a round: as many 3:3 games as the players allow, then 2:2 games
     * for the rest. With fewer sets than that needs, the players left over sit the round out.
     */
    static List<Integer> setTeamSizes(int playersPerTeam, int sets) {
        int threes = sets * 3 <= playersPerTeam ? sets : Math.max(0, playersPerTeam - 2 * sets);
        List<Integer> sizes = new ArrayList<>();
        for (int index = 0; index < sets; index++) {
            sizes.add(index < threes ? 3 : 2);
        }
        return sizes;
    }

    private Set<Long> teamPlayerIds(List<CaptainDraftParticipant> participants, String team) {
        return participants
            .stream()
            .filter(participant -> team.equals(normalizeTeam(participant.getTeam())))
            .map(participant -> participant.getPlayer().getId())
            .collect(Collectors.toSet());
    }

    // One side of a set, slot by slot: each named player from that side's team, once.
    private List<Player> sidePlayers(
        List<Long> playerIds,
        Integer teamSize,
        Set<Long> teamPlayerIds,
        Map<Long, CaptainDraftParticipant> participantsByPlayerId
    ) {
        int size = teamSize == null || teamSize < 1 ? 1 : teamSize;
        if (playerIds.size() > size) {
            throw new IllegalArgumentException("A side of this set takes at most " + size + " players");
        }
        List<Player> side = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int index = 0; index < size; index++) {
            Long playerId = index < playerIds.size() ? playerIds.get(index) : null;
            if (playerId == null) {
                side.add(null);
                continue;
            }
            if (!teamPlayerIds.contains(playerId)) {
                throw new IllegalArgumentException("Entry player must belong to that side's team");
            }
            if (!seen.add(playerId)) {
                throw new IllegalArgumentException("A player can take only one slot of a set");
            }
            side.add(participantsByPlayerId.get(playerId).getPlayer());
        }
        return side;
    }

    private boolean isSideFull(CaptainDraftEntry entry, String team) {
        return entry.sidePlayers(team).stream().allMatch(Objects::nonNull);
    }

    // Everyone plays at most once a round; the rest of a round's players sit it out.
    private void requireOneSetPerRound(List<CaptainDraftEntry> entries) {
        Map<Integer, Set<Long>> playersByRound = new HashMap<>();
        for (CaptainDraftEntry entry : entries) {
            Set<Long> roundPlayers = playersByRound.computeIfAbsent(entry.getRoundNumber(), ignored -> new HashSet<>());
            for (String team : List.of(TEAM_HOME, TEAM_AWAY)) {
                for (Player player : entry.sidePlayers(team)) {
                    if (player != null && !roundPlayers.add(player.getId())) {
                        throw new IllegalArgumentException("A player can play only one set a round");
                    }
                }
            }
        }
    }

    /**
     * The races a set smaller than its round's composition starts with; the host can change them. A
     * 2:2 game plays PPP as PP, PPT as PT, PPZ as PZ and PTZ as PR, any other composition less one
     * Protoss (PRR -> RR, RRR -> RR); a 1:1 game the first race of that.
     */
    static String setComposition(String roundCode, int teamSize) {
        if (roundCode.length() <= teamSize) {
            return roundCode;
        }
        if (teamSize <= 1) {
            return setComposition(roundCode, 2).substring(0, 1);
        }
        String known = teamSize == 2 ? TWO_A_SIDE.get(roundCode) : null;
        if (known != null) {
            return known;
        }
        String code = roundCode;
        while (code.length() > teamSize) {
            int protoss = code.indexOf('P');
            code = protoss >= 0 ? code.substring(0, protoss) + code.substring(protoss + 1) : code.substring(1);
        }
        return code;
    }

    // One to three races out of P, T, Z and R (random), kept in that order: "rpr" becomes "PRR".
    private String normalizeRaceCode(String raceCode) {
        String normalized = safeTrim(raceCode).toUpperCase();
        if (normalized.isEmpty() || normalized.length() > 3 || !normalized.chars().allMatch(race -> RACE_ORDER.indexOf(race) >= 0)) {
            throw new IllegalArgumentException("raceCode must be one to three of P, T, Z and R");
        }
        return normalized
            .chars()
            .mapToObj(race -> String.valueOf((char) race))
            .sorted(Comparator.comparingInt(RACE_ORDER::indexOf))
            .collect(Collectors.joining());
    }

    private String normalizeRequiredTier(String requiredTier) {
        String normalized = safeTrim(requiredTier);
        if (normalized.length() > MAX_REQUIRED_TIER_LENGTH) {
            throw new IllegalArgumentException("requiredTier must be at most " + MAX_REQUIRED_TIER_LENGTH + " characters");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isPlayer(Player player, Long playerId) {
        return player != null && playerId.equals(player.getId());
    }

    private CaptainDraft loadDraft(Long groupId, Long draftId) {
        return captainDraftRepository.findByIdAndGroup_Id(draftId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Draft not found: " + draftId));
    }

    private String resolveTitle(String title) {
        String normalized = safeTrim(title);
        return normalized.isEmpty() ? "정기 감전 드래프트" : normalized;
    }

    /**
     * The team size of each set of a round, as the host lays them out: 3:3, 2:2 and 1:1 games in any
     * mix that a team's players can fill, the rest sitting the round out. Without a layout, a number
     * of sets (3:3 first, then 2:2), or enough sets for everyone to play.
     */
    private List<Integer> resolveSetSizes(List<Integer> requestedSizes, Integer requestedSets, int playersPerTeam) {
        if (requestedSizes != null && !requestedSizes.isEmpty()) {
            if (requestedSizes.size() > MAX_SETS_PER_ROUND) {
                throw new IllegalArgumentException("A round has at most " + MAX_SETS_PER_ROUND + " sets");
            }
            int players = 0;
            for (Integer size : requestedSizes) {
                if (size == null || size < 1 || size > 3) {
                    throw new IllegalArgumentException("Each set is 3:3, 2:2 or 1:1");
                }
                players += size;
            }
            if (players > playersPerTeam) {
                throw new IllegalArgumentException("The sets need " + players + " players a team, but a team has " + playersPerTeam);
            }
            return List.copyOf(requestedSizes);
        }
        int maximum = Math.max(1, playersPerTeam / 2);
        if (requestedSets == null) {
            return setTeamSizes(playersPerTeam, Math.min(maximum, (playersPerTeam + 2) / 3));
        }
        if (requestedSets < 1 || requestedSets > maximum) {
            throw new IllegalArgumentException("setsPerRound must be between 1 and " + maximum);
        }
        return setTeamSizes(playersPerTeam, requestedSets);
    }

    private int resolveRoundCount(Integer requestedRounds) {
        if (requestedRounds == null) {
            return DEFAULT_ROUND_COUNT;
        }
        if (requestedRounds < 1 || requestedRounds > MAX_ROUND_COUNT) {
            throw new IllegalArgumentException("roundCount must be between 1 and " + MAX_ROUND_COUNT);
        }
        return requestedRounds;
    }

    private List<Long> normalizeDistinctPlayerIds(List<Long> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return List.of();
        }

        Set<Long> distinctIds = new LinkedHashSet<>();
        for (Long playerId : playerIds) {
            if (playerId == null || playerId <= 0) {
                throw new IllegalArgumentException("Player IDs must be positive numbers");
            }
            if (!distinctIds.add(playerId)) {
                throw new IllegalArgumentException("Duplicate player IDs are not allowed");
            }
        }
        return new ArrayList<>(distinctIds);
    }

    private int nextPickOrder(List<CaptainDraftParticipant> participants) {
        return participants
            .stream()
            .map(CaptainDraftParticipant::getPickOrder)
            .filter(order -> order != null && order > 0)
            .max(Integer::compareTo)
            .orElse(0) + 1;
    }

    private CaptainDraftResponse toResponse(
        CaptainDraft draft,
        List<CaptainDraftParticipant> participants,
        List<CaptainDraftEntry> entries
    ) {
        List<CaptainDraftParticipantResponse> participantResponses = participants
            .stream()
            .sorted(
                Comparator
                    .comparingInt((CaptainDraftParticipant participant) ->
                        teamRank(normalizeTeam(participant.getTeam()))
                    )
                    .thenComparing(participant -> !participant.isCaptain())
                    .thenComparing(
                        participant -> participant.getPickOrder() == null
                            ? Integer.MAX_VALUE
                            : participant.getPickOrder()
                    )
                    .thenComparing(participant ->
                        safeTrim(PlayerIdentityPolicy.responseNickname(participant.getPlayer())).toLowerCase()
                    )
            )
            .map(participant -> new CaptainDraftParticipantResponse(
                responsePlayerId(participant.getPlayer()),
                responseNickname(participant.getPlayer()),
                PlayerIdentityPolicy.isIdentityHidden(participant.getPlayer())
                    ? null
                    : normalizeRace(participant.getPlayer().getRace()),
                normalizeTeam(participant.getTeam()),
                participant.isCaptain(),
                participant.getPickOrder()
            ))
            .toList();

        Map<String, Player> captainByTeam = new HashMap<>();
        captainByTeam.put(TEAM_HOME, draft.getHomeCaptain());
        captainByTeam.put(TEAM_AWAY, draft.getAwayCaptain());

        List<CaptainDraftPickLogResponse> pickResponses = participants
            .stream()
            .filter(participant -> !participant.isCaptain())
            .filter(participant -> participant.getPickOrder() != null)
            .sorted(Comparator.comparing(CaptainDraftParticipant::getPickOrder))
            .map(participant -> {
                String team = normalizeTeam(participant.getTeam());
                Player captain = captainByTeam.get(team);
                return new CaptainDraftPickLogResponse(
                    participant.getPickOrder(),
                    responsePlayerId(captain),
                    captain == null ? "-" : responseNickname(captain),
                    responsePlayerId(participant.getPlayer()),
                    responseNickname(participant.getPlayer()),
                    team
                );
            })
            .toList();

        List<CaptainDraftEntryResponse> entryResponses = entries
            .stream()
            .sorted(
                Comparator
                    .comparing(CaptainDraftEntry::getRoundNumber)
                    .thenComparing(CaptainDraftEntry::getSetNumber)
            )
            .map(entry -> new CaptainDraftEntryResponse(
                safeInt(entry.getRoundNumber()),
                safeTrim(entry.getRoundCode()),
                safeInt(entry.getSetNumber()),
                entry.sidePlayers(TEAM_HOME).size(),
                entry.getRequiredTier(),
                sideResponse(entry, TEAM_HOME),
                sideResponse(entry, TEAM_AWAY),
                normalizeWinnerTeam(entry.getWinnerTeam())
            ))
            .toList();

        return new CaptainDraftResponse(
            draft.getId(),
            draft.getGroup().getId(),
            draft.getTitle(),
            safeTrim(draft.getStatus()),
            safeInt(draft.getSetsPerRound()),
            participants.size(),
            normalizeTeam(draft.getCurrentTurnTeam()),
            responsePlayerId(draft.getHomeCaptain()),
            responseNickname(draft.getHomeCaptain()),
            responsePlayerId(draft.getAwayCaptain()),
            responseNickname(draft.getAwayCaptain()),
            participantResponses,
            pickResponses,
            entryResponses
        );
    }

    private int teamRank(String team) {
        return switch (team) {
            case TEAM_HOME -> 0;
            case TEAM_AWAY -> 1;
            default -> 2;
        };
    }

    private List<CaptainDraftEntryPlayerResponse> sideResponse(CaptainDraftEntry entry, String team) {
        return entry.sidePlayers(team)
            .stream()
            .map(player -> player == null
                ? null
                : new CaptainDraftEntryPlayerResponse(responsePlayerId(player), responseNickname(player)))
            .toList();
    }

    private Long responsePlayerId(Player player) {
        return PlayerIdentityPolicy.responsePlayerId(player);
    }

    private void requireActiveDraftParticipants(List<CaptainDraftParticipant> participants) {
        boolean hasHiddenParticipant = participants == null || participants.stream()
            .anyMatch(participant -> participant == null
                || PlayerIdentityPolicy.isIdentityHidden(participant.getPlayer()));
        if (hasHiddenParticipant) {
            throw new IllegalArgumentException("Draft contains an inactive participant");
        }
    }

    private String responseNickname(Player player) {
        return PlayerIdentityPolicy.responseNickname(player);
    }

    private String normalizeTeam(String team) {
        String normalized = safeTrim(team).toUpperCase();
        return switch (normalized) {
            case TEAM_HOME -> TEAM_HOME;
            case TEAM_AWAY -> TEAM_AWAY;
            default -> TEAM_UNASSIGNED;
        };
    }

    private String normalizeRace(String race) {
        return PlayerRacePolicy.toDisplayRace(race);
    }

    private String normalizeWinnerTeam(String winnerTeam) {
        String normalized = safeTrim(winnerTeam).toUpperCase();
        if (normalized.isEmpty()) {
            return null;
        }
        return switch (normalized) {
            case TEAM_HOME -> TEAM_HOME;
            case TEAM_AWAY -> TEAM_AWAY;
            default -> throw new IllegalArgumentException("winnerTeam must be HOME or AWAY");
        };
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String entryKey(Integer roundNumber, Integer setNumber) {
        return roundNumber + ":" + setNumber;
    }
}
