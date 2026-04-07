package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.CaptainDraftCreateRequest;
import com.balancify.backend.api.group.dto.CaptainDraftEntriesUpdateRequest;
import com.balancify.backend.api.group.dto.CaptainDraftEntryResponse;
import com.balancify.backend.api.group.dto.CaptainDraftEntryUpdateItemRequest;
import com.balancify.backend.api.group.dto.CaptainDraftParticipantResponse;
import com.balancify.backend.api.group.dto.CaptainDraftPickLogResponse;
import com.balancify.backend.api.group.dto.CaptainDraftPickRequest;
import com.balancify.backend.api.group.dto.CaptainDraftResponse;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
    private static final List<String> ROUND_CODES = List.of("PPP", "PPT", "PPZ", "PTZ");

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

        List<Player> players = playerRepository.findByGroup_IdAndIdIn(groupId, participantPlayerIds);
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
        draft.setSetsPerRound(resolveSetsPerRound(request.setsPerRound(), participantPlayerIds.size()));
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
        for (int roundIndex = 0; roundIndex < ROUND_CODES.size(); roundIndex++) {
            int roundNumber = roundIndex + 1;
            String roundCode = ROUND_CODES.get(roundIndex);
            for (int setNumber = 1; setNumber <= savedDraft.getSetsPerRound(); setNumber++) {
                CaptainDraftEntry entry = new CaptainDraftEntry();
                entry.setDraft(savedDraft);
                entry.setRoundNumber(roundNumber);
                entry.setRoundCode(roundCode);
                entry.setSetNumber(setNumber);
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
        Map<Long, CaptainDraftParticipant> participantsByPlayerId = participants
            .stream()
            .collect(Collectors.toMap(participant -> participant.getPlayer().getId(), participant -> participant));

        CaptainDraftParticipant captain = participantsByPlayerId.get(request.captainPlayerId());
        if (captain == null || !captain.isCaptain()) {
            throw new IllegalArgumentException("Invalid captain player ID");
        }

        String captainTeam = normalizeTeam(captain.getTeam());
        if (!captainTeam.equals(safeTrim(draft.getCurrentTurnTeam()))) {
            throw new IllegalArgumentException("It is not this captain's turn");
        }

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

    @Transactional
    public CaptainDraftResponse updateEntries(
        Long groupId,
        Long draftId,
        CaptainDraftEntriesUpdateRequest request
    ) {
        if (request == null || request.captainPlayerId() == null) {
            throw new IllegalArgumentException("captainPlayerId is required");
        }
        if (request.entries() == null || request.entries().isEmpty()) {
            throw new IllegalArgumentException("entries are required");
        }

        CaptainDraft draft = loadDraft(groupId, draftId);
        List<CaptainDraftParticipant> participants =
            captainDraftParticipantRepository.findByDraftIdWithPlayer(draft.getId());

        Map<Long, CaptainDraftParticipant> participantsByPlayerId = participants
            .stream()
            .collect(Collectors.toMap(participant -> participant.getPlayer().getId(), participant -> participant));

        CaptainDraftParticipant captain = participantsByPlayerId.get(request.captainPlayerId());
        if (captain == null || !captain.isCaptain()) {
            throw new IllegalArgumentException("Invalid captain player ID");
        }

        String captainTeam = normalizeTeam(captain.getTeam());
        if (TEAM_UNASSIGNED.equals(captainTeam)) {
            throw new IllegalArgumentException("Captain team is not assigned");
        }

        Set<Long> captainTeamPlayerIds = participants
            .stream()
            .filter(participant -> captainTeam.equals(normalizeTeam(participant.getTeam())))
            .map(participant -> participant.getPlayer().getId())
            .collect(Collectors.toSet());

        List<CaptainDraftEntry> entries = captainDraftEntryRepository.findByDraftIdWithPlayers(draft.getId());
        Map<String, CaptainDraftEntry> entriesByKey = new HashMap<>();
        for (CaptainDraftEntry entry : entries) {
            entriesByKey.put(entryKey(entry.getRoundNumber(), entry.getSetNumber()), entry);
        }

        for (CaptainDraftEntryUpdateItemRequest updateItem : request.entries()) {
            if (updateItem == null) {
                continue;
            }
            if (updateItem.roundNumber() == null
                || updateItem.roundNumber() < 1
                || updateItem.roundNumber() > ROUND_CODES.size()) {
                throw new IllegalArgumentException("roundNumber must be between 1 and 4");
            }
            if (updateItem.setNumber() == null
                || updateItem.setNumber() < 1
                || updateItem.setNumber() > draft.getSetsPerRound()) {
                throw new IllegalArgumentException(
                    "setNumber must be between 1 and " + draft.getSetsPerRound()
                );
            }

            CaptainDraftEntry targetEntry = entriesByKey.get(
                entryKey(updateItem.roundNumber(), updateItem.setNumber())
            );
            if (targetEntry == null) {
                throw new IllegalArgumentException("Invalid round/set combination");
            }

            Long playerId = updateItem.playerId();
            Player selectedPlayer = null;
            if (playerId != null) {
                if (!captainTeamPlayerIds.contains(playerId)) {
                    throw new IllegalArgumentException("Entry player must belong to captain's team");
                }
                CaptainDraftParticipant selectedParticipant = participantsByPlayerId.get(playerId);
                if (selectedParticipant == null) {
                    throw new IllegalArgumentException("Entry player not found in draft participants");
                }
                selectedPlayer = selectedParticipant.getPlayer();
            }

            if (TEAM_HOME.equals(captainTeam)) {
                targetEntry.setHomePlayer(selectedPlayer);
            } else {
                targetEntry.setAwayPlayer(selectedPlayer);
            }
        }

        captainDraftEntryRepository.saveAll(entries);
        return toResponse(draft, participants, entries);
    }

    private CaptainDraft loadDraft(Long groupId, Long draftId) {
        return captainDraftRepository.findByIdAndGroup_Id(draftId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Draft not found: " + draftId));
    }

    private String resolveTitle(String title) {
        String normalized = safeTrim(title);
        return normalized.isEmpty() ? "정기 감전 드래프트" : normalized;
    }

    private int resolveSetsPerRound(Integer requestedSetsPerRound, int participantCount) {
        if (requestedSetsPerRound != null) {
            if (requestedSetsPerRound < 1 || requestedSetsPerRound > 8) {
                throw new IllegalArgumentException("setsPerRound must be between 1 and 8");
            }
            return requestedSetsPerRound;
        }

        int extraByParticipants = Math.max(0, (participantCount - 8) / 4);
        return Math.min(8, 4 + extraByParticipants);
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
                        safeTrim(participant.getPlayer().getNickname()).toLowerCase()
                    )
            )
            .map(participant -> new CaptainDraftParticipantResponse(
                participant.getPlayer().getId(),
                participant.getPlayer().getNickname(),
                normalizeRace(participant.getPlayer().getRace()),
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
                    captain == null ? null : captain.getId(),
                    captain == null ? "-" : captain.getNickname(),
                    participant.getPlayer().getId(),
                    participant.getPlayer().getNickname(),
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
                entry.getHomePlayer() == null ? null : entry.getHomePlayer().getId(),
                entry.getHomePlayer() == null ? null : entry.getHomePlayer().getNickname(),
                entry.getAwayPlayer() == null ? null : entry.getAwayPlayer().getId(),
                entry.getAwayPlayer() == null ? null : entry.getAwayPlayer().getNickname()
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
            draft.getHomeCaptain().getId(),
            draft.getHomeCaptain().getNickname(),
            draft.getAwayCaptain().getId(),
            draft.getAwayCaptain().getNickname(),
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
