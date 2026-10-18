package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.CaptainDraftAceRequest;
import com.balancify.backend.api.group.dto.CaptainDraftCreateRequest;
import com.balancify.backend.api.group.dto.CaptainDraftEntriesUpdateRequest;
import com.balancify.backend.api.group.dto.CaptainDraftEntryModeRequest;
import com.balancify.backend.api.group.dto.CaptainDraftEntryPlayerResponse;
import com.balancify.backend.api.group.dto.CaptainDraftEntryResponse;
import com.balancify.backend.api.group.dto.CaptainDraftEntryUpdateItemRequest;
import com.balancify.backend.api.group.dto.CaptainDraftLineupRequest;
import com.balancify.backend.api.group.dto.CaptainDraftLineupResponse;
import com.balancify.backend.api.group.dto.CaptainDraftLineupSetRequest;
import com.balancify.backend.api.group.dto.CaptainDraftListResponse;
import com.balancify.backend.api.group.dto.CaptainDraftParticipantResponse;
import com.balancify.backend.api.group.dto.CaptainDraftPickLogResponse;
import com.balancify.backend.api.group.dto.CaptainDraftPickRequest;
import com.balancify.backend.api.group.dto.CaptainDraftResponse;
import com.balancify.backend.api.group.dto.CaptainDraftSummaryResponse;
import com.balancify.backend.api.group.dto.CaptainDraftUnpickRequest;
import com.balancify.backend.domain.CaptainDraft;
import com.balancify.backend.domain.CaptainDraftEntry;
import com.balancify.backend.domain.CaptainDraftLineup;
import com.balancify.backend.domain.CaptainDraftParticipant;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.CaptainDraftEntryRepository;
import com.balancify.backend.repository.CaptainDraftLineupRepository;
import com.balancify.backend.repository.CaptainDraftParticipantRepository;
import com.balancify.backend.repository.CaptainDraftRepository;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.service.exception.CaptainDraftLineupForbiddenException;
import com.balancify.backend.service.exception.CaptainDraftResultLockedException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaptainDraftService {

    private static final String TEAM_HOME = "HOME";
    private static final String TEAM_AWAY = "AWAY";
    private static final String TEAM_UNASSIGNED = "UNASSIGNED";
    private static final String STATUS_DRAFTING = "DRAFTING";
    private static final String STATUS_READY = "READY";
    // The host fills both teams in for everyone to see, or each captain hands in their own team.
    private static final String ENTRY_MODE_HOST = "HOST";
    private static final String ENTRY_MODE_CAPTAINS = "CAPTAINS";
    // What a captain hands in at once: every regular round, then the ace decider if one is played.
    private static final String STAGE_MAIN = "MAIN";
    private static final String STAGE_ACE = "ACE";
    // Rounds take these compositions in turn unless the host picks others.
    private static final List<String> DEFAULT_ROUND_CODES = List.of("PPP", "PPT", "PPZ", "PTZ");
    private static final String RACE_ORDER = "PTZR";
    // The 2:2 game each usual round composition is played with, as the clan plays them.
    private static final Map<String, String> TWO_A_SIDE = Map.of("PPP", "PP", "PPT", "PT", "PPZ", "PZ", "PTZ", "PR");
    private static final int DEFAULT_ROUND_COUNT = 4;
    private static final int MAX_ROUND_COUNT = 7;
    private static final int MAX_SETS_PER_ROUND = 10;
    private static final int MAX_REQUIRED_TIER_LENGTH = 30;
    // captain_drafts.title is varchar(120).
    private static final int MAX_TITLE_LENGTH = 120;
    // created_by_nickname and result_recorded_by_nickname are varchar(100).
    private static final int MAX_ACTOR_NICKNAME_LENGTH = 100;
    // Drafts listed as records; older ones stay in the database.
    private static final int RECORD_LIMIT = 30;

    private final GroupRepository groupRepository;
    private final PlayerRepository playerRepository;
    private final CaptainDraftRepository captainDraftRepository;
    private final CaptainDraftParticipantRepository captainDraftParticipantRepository;
    private final CaptainDraftEntryRepository captainDraftEntryRepository;
    private final CaptainDraftLineupRepository captainDraftLineupRepository;
    private final OperationAuditLogService operationAuditLogService;

    public CaptainDraftService(
        GroupRepository groupRepository,
        PlayerRepository playerRepository,
        CaptainDraftRepository captainDraftRepository,
        CaptainDraftParticipantRepository captainDraftParticipantRepository,
        CaptainDraftEntryRepository captainDraftEntryRepository,
        CaptainDraftLineupRepository captainDraftLineupRepository,
        OperationAuditLogService operationAuditLogService
    ) {
        this.groupRepository = groupRepository;
        this.playerRepository = playerRepository;
        this.captainDraftRepository = captainDraftRepository;
        this.captainDraftParticipantRepository = captainDraftParticipantRepository;
        this.captainDraftEntryRepository = captainDraftEntryRepository;
        this.captainDraftLineupRepository = captainDraftLineupRepository;
        this.operationAuditLogService = operationAuditLogService;
    }

    /**
     * Starts a draft; whoever started it is kept with it, the nickname shown and the email not. A
     * draft run by its captains needs both captains' players linked to a site account, as only a
     * captain signed in as themselves hands their team in.
     *
     * Every answer below is for one reader (viewerUserId: the verified account asking, or null), who
     * is shown a team's players of a stage not yet in for both teams only when on that team.
     */
    @Transactional
    public CaptainDraftResponse createDraft(
        Long groupId,
        CaptainDraftCreateRequest request,
        String actorEmail,
        String actorNickname,
        UUID viewerUserId
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        String entryMode = resolveEntryMode(request.entryMode());
        String title = resolveTitle(request.title());
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
        if (ENTRY_MODE_CAPTAINS.equals(entryMode)) {
            requireLinkedCaptains(homeCaptain, awayCaptain);
        }

        CaptainDraft draft = new CaptainDraft();
        draft.setGroup(group);
        draft.setTitle(title);
        draft.setEntryMode(entryMode);
        draft.setStatus(STATUS_DRAFTING);
        int playersPerTeam = participantPlayerIds.size() / 2;
        List<Integer> teamSizes = resolveSetSizes(request.setSizes(), request.setsPerRound(), playersPerTeam);
        int roundCount = resolveRoundCount(request.roundCount());
        draft.setSetsPerRound(teamSizes.size());
        draft.setCurrentTurnTeam(TEAM_HOME);
        draft.setHomeCaptain(homeCaptain);
        draft.setAwayCaptain(awayCaptain);
        draft.setCreatedByEmail(actorEmailOrNull(actorEmail));
        draft.setCreatedByNickname(actorNicknameOrNull(actorNickname));
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

        return toResponse(savedDraft, participants, entries, List.of(), viewerUserId);
    }

    @Transactional(readOnly = true)
    public CaptainDraftResponse getDraft(Long groupId, Long draftId, UUID viewerUserId) {
        CaptainDraft draft = loadDraft(groupId, draftId);
        return readDraft(draft, viewerUserId);
    }

    /**
     * The latest drafts with how each went, newest first. A draft's results are kept with it only;
     * they are never matches and never move anyone's MMR.
     */
    @Transactional(readOnly = true)
    public CaptainDraftListResponse listDrafts(Long groupId) {
        List<CaptainDraft> drafts = captainDraftRepository.findRecentWithCaptains(groupId, PageRequest.of(0, RECORD_LIMIT));
        if (drafts.isEmpty()) {
            return new CaptainDraftListResponse(List.of());
        }
        List<Long> draftIds = drafts.stream().map(CaptainDraft::getId).toList();
        Map<Long, List<CaptainDraftEntry>> entriesByDraft = captainDraftEntryRepository.findByDraftIds(draftIds)
            .stream()
            .collect(Collectors.groupingBy(entry -> entry.getDraft().getId()));
        Map<Long, Long> participantCounts = new HashMap<>();
        captainDraftParticipantRepository.countByDraftIds(draftIds)
            .forEach(row -> participantCounts.put(row.getDraftId(), row.getParticipants()));
        return new CaptainDraftListResponse(drafts
            .stream()
            .map(draft -> summarize(
                draft,
                entriesByDraft.getOrDefault(draft.getId(), List.of()),
                participantCounts.getOrDefault(draft.getId(), 0L)
            ))
            .toList());
    }

    /**
     * Removes a draft with its participants, its sets and their players, such as one made to try
     * the page out. A draft never made a match, so no MMR, point or ranking changes with it. The
     * operation log keeps who removed which, with how many players and sets it had. Answers with
     * the records as they now are.
     */
    @Transactional
    public CaptainDraftListResponse deleteDraft(Long groupId, Long draftId, String actorEmail, String actorNickname) {
        CaptainDraft draft = loadDraft(groupId, draftId);
        Long id = draft.getId();
        String title = draft.getTitle();
        int sets = captainDraftEntryRepository.deleteByDraftId(id);
        int participants = captainDraftParticipantRepository.deleteByDraftId(id);
        captainDraftRepository.deleteDraft(id);
        operationAuditLogService.recordCaptainDraftDeleted(
            actorEmail, actorNickname, groupId, id, title, "participants=" + participants + ", sets=" + sets
        );
        return listDrafts(groupId);
    }

    @Transactional(readOnly = true)
    public CaptainDraftResponse getLatestDraft(Long groupId, UUID viewerUserId) {
        CaptainDraft draft = captainDraftRepository.findTopByGroup_IdOrderByCreatedAtDescIdDesc(groupId)
            .orElseThrow(() -> new NoSuchElementException("Draft not found for group: " + groupId));
        return readDraft(draft, viewerUserId);
    }

    private CaptainDraftResponse readDraft(CaptainDraft draft, UUID viewerUserId) {
        List<CaptainDraftParticipant> participants =
            captainDraftParticipantRepository.findByDraftIdWithPlayer(draft.getId());
        List<CaptainDraftEntry> entries = captainDraftEntryRepository.findByDraftIdWithPlayers(draft.getId());
        return toResponse(draft, participants, entries, lineupsOf(draft), viewerUserId);
    }

    @Transactional
    public CaptainDraftResponse pickPlayer(
        Long groupId,
        Long draftId,
        CaptainDraftPickRequest request,
        UUID viewerUserId
    ) {
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
        return toResponse(draft, participants, entries, lineupsOf(draft), viewerUserId);
    }

    /**
     * Takes a picked player back out of their team, for a pick entered by mistake: out of the sets
     * that named them, and a finished draft is open again. Only before any set has a result: once
     * the games have begun, taking a pick back would wipe a result, which nobody may do this way.
     * Nor once a captain has handed a line-up in, which taking a player out would quietly change.
     */
    @Transactional
    public CaptainDraftResponse unpickPlayer(
        Long groupId,
        Long draftId,
        CaptainDraftUnpickRequest request,
        UUID viewerUserId
    ) {
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
        List<CaptainDraftEntry> entries = captainDraftEntryRepository.findByDraftIdWithPlayers(draft.getId());
        if (entries.stream().anyMatch(entry -> normalizeWinnerTeam(entry.getWinnerTeam()) != null)) {
            throw new IllegalStateException("A pick cannot be taken back once a set has a result");
        }
        List<CaptainDraftLineup> lineups = lineupsOf(draft);
        if (!lineups.isEmpty()) {
            throw new IllegalStateException("A pick cannot be taken back once a captain has handed a line-up in");
        }

        participant.setTeam(null);
        participant.setPickOrder(null);
        captainDraftParticipantRepository.save(participant);

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
            }
        }
        captainDraftEntryRepository.saveAll(entries);

        if (STATUS_READY.equalsIgnoreCase(safeTrim(draft.getStatus()))) {
            draft.setStatus(STATUS_DRAFTING);
            draft.setCurrentTurnTeam(team);
            captainDraftRepository.save(draft);
        }
        return toResponse(draft, participants, entries, lineups, viewerUserId);
    }

    /**
     * Saves the sets as the host fills them in, each with its race composition. Each side of a set takes
     * up to its team size of its own team's players, a player plays at most one set a round, and a
     * set gets a winner only with both sides full.
     *
     * Any member with access fills in the sets that have no result yet and enters a set's first
     * result. A set that already has a result is changed (its result, players, races or tier note)
     * only by those who may edit results: admins and match result editors. Anyone else may send it
     * back unchanged, as the page sends every set; a change to it is refused, and nothing is saved.
     * A set whose result is put in or changed names who did it and when; one taken out names nobody.
     *
     * In a draft run by its captains, the players of a stage not yet in for both teams are the
     * captains' to hand in (submitLineup): what is sent for them here is ignored, and no set of that
     * stage takes a result yet. Races and tier notes stay anyone's, and once both line-ups are in, so
     * are the players, as in a draft the host fills in.
     */
    @Transactional
    public CaptainDraftResponse updateEntries(
        Long groupId,
        Long draftId,
        CaptainDraftEntriesUpdateRequest request,
        boolean canEditResults,
        String actorEmail,
        String actorNickname,
        UUID viewerUserId
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
        boolean byCaptains = isRunByCaptains(draft);
        List<CaptainDraftLineup> lineups = lineupsOf(draft);
        Map<String, Set<String>> teamsIn = teamsInByStage(lineups);

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

            List<Player> home = sidePlayers(
                updateItem.homePlayerIds(), targetEntry.getTeamSize(), teamPlayerIds.get(TEAM_HOME), participantsByPlayerId
            );
            List<Player> away = sidePlayers(
                updateItem.awayPlayerIds(), targetEntry.getTeamSize(), teamPlayerIds.get(TEAM_AWAY), participantsByPlayerId
            );
            String requiredTier = normalizeRequiredTier(updateItem.requiredTier());
            // Races left out are kept.
            String raceCode = updateItem.raceCode() == null ? targetEntry.getRoundCode() : normalizeRaceCode(updateItem.raceCode());
            String winnerTeam = normalizeWinnerTeam(updateItem.winnerTeam());
            String savedWinnerTeam = normalizeWinnerTeam(targetEntry.getWinnerTeam());
            if (byCaptains && teamsIn.getOrDefault(stageOf(targetEntry), Set.of()).size() < 2) {
                // Not shown to everyone yet: what was sent for the players is not what the captains handed in.
                home = targetEntry.sidePlayers(TEAM_HOME);
                away = targetEntry.sidePlayers(TEAM_AWAY);
                if (winnerTeam != null) {
                    throw new IllegalArgumentException(
                        "Round " + targetEntry.getRoundNumber() + " has no result before both line-ups are in"
                    );
                }
            }
            if (!canEditResults
                && savedWinnerTeam != null
                && changesSet(targetEntry, home, away, raceCode, requiredTier, winnerTeam)) {
                throw new CaptainDraftResultLockedException(
                    "Round " + targetEntry.getRoundNumber() + " set " + targetEntry.getSetNumber()
                        + " already has a result; only admins and result editors can change it"
                );
            }

            targetEntry.setSidePlayers(TEAM_HOME, home);
            targetEntry.setSidePlayers(TEAM_AWAY, away);
            targetEntry.setRequiredTier(requiredTier);
            targetEntry.setRoundCode(raceCode);
            if (winnerTeam != null && (!isSideFull(targetEntry, TEAM_HOME) || !isSideFull(targetEntry, TEAM_AWAY))) {
                throw new IllegalArgumentException("Winner team requires both home and away players");
            }
            targetEntry.setWinnerTeam(winnerTeam);
            if (!Objects.equals(savedWinnerTeam, winnerTeam)) {
                if (winnerTeam == null) {
                    targetEntry.setResultRecordedBy(null, null, null);
                } else {
                    targetEntry.setResultRecordedBy(
                        actorEmailOrNull(actorEmail),
                        actorNicknameOrNull(actorNickname),
                        OffsetDateTime.now()
                    );
                }
            }
        }

        requireOneSetPerRound(entries);
        captainDraftEntryRepository.saveAll(entries);
        return toResponse(draft, participants, entries, lineups, viewerUserId);
    }

    /**
     * A captain hands in their team's line-up for a stage of a draft run by its captains, once: for
     * MAIN their team's players in every set of every regular round, for ACE in the ace decider;
     * every slot filled, each player once a round. Until the other captain hands theirs in, it is
     * shown to their own team only; once both are in, both are shown to everyone.
     */
    @Transactional
    public CaptainDraftResponse submitLineup(
        Long groupId,
        Long draftId,
        CaptainDraftLineupRequest request,
        UUID viewerUserId
    ) {
        if (request == null || request.sets() == null || request.sets().isEmpty()) {
            throw new IllegalArgumentException("sets are required");
        }
        String stage = resolveStage(request.stage());
        CaptainDraft draft = loadDraft(groupId, draftId);
        if (!isRunByCaptains(draft)) {
            throw new IllegalArgumentException("This draft's sets are filled in by the host");
        }
        List<CaptainDraftParticipant> participants =
            captainDraftParticipantRepository.findByDraftIdWithPlayer(draft.getId());
        requireActiveDraftParticipants(participants);
        CaptainDraftParticipant captain = viewerParticipant(participants, viewerUserId);
        if (captain == null || !captain.isCaptain()) {
            throw new CaptainDraftLineupForbiddenException("Only a captain of this draft hands in a line-up");
        }
        String team = normalizeTeam(captain.getTeam());

        List<CaptainDraftEntry> entries = captainDraftEntryRepository.findByDraftIdWithPlayers(draft.getId());
        Map<String, CaptainDraftEntry> stageSets = new HashMap<>();
        for (CaptainDraftEntry entry : entries) {
            if (stage.equals(stageOf(entry))) {
                stageSets.put(entryKey(entry.getRoundNumber(), entry.getSetNumber()), entry);
            }
        }
        if (stageSets.isEmpty()) {
            throw new IllegalArgumentException("This draft has no ace decider");
        }
        List<CaptainDraftLineup> lineups = lineupsOf(draft);
        if (teamsInByStage(lineups).getOrDefault(stage, Set.of()).contains(team)) {
            throw new IllegalStateException("This team's line-up is handed in already; a line-up is handed in once");
        }

        Map<String, List<Long>> sent = new HashMap<>();
        for (CaptainDraftLineupSetRequest set : request.sets()) {
            String key = set == null ? null : entryKey(set.roundNumber(), set.setNumber());
            if (key == null || !stageSets.containsKey(key)) {
                throw new IllegalArgumentException("Invalid set for this line-up");
            }
            if (sent.put(key, set.playerIds() == null ? List.of() : set.playerIds()) != null) {
                throw new IllegalArgumentException("A set is sent once");
            }
        }
        if (!sent.keySet().equals(stageSets.keySet())) {
            throw new IllegalArgumentException("A line-up takes every set of every round it is for");
        }

        Map<Long, CaptainDraftParticipant> participantsByPlayerId = participants
            .stream()
            .collect(Collectors.toMap(participant -> participant.getPlayer().getId(), participant -> participant));
        Set<Long> ownTeam = teamPlayerIds(participants, team);
        for (Map.Entry<String, CaptainDraftEntry> set : stageSets.entrySet()) {
            CaptainDraftEntry entry = set.getValue();
            List<Player> side = sidePlayers(sent.get(set.getKey()), entry.getTeamSize(), ownTeam, participantsByPlayerId);
            if (side.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Every slot of a line-up is filled");
            }
            entry.setSidePlayers(team, side);
        }
        requireOneSetPerRound(entries);
        captainDraftEntryRepository.saveAll(entries);

        CaptainDraftLineup lineup = new CaptainDraftLineup();
        lineup.setDraft(draft);
        lineup.setStage(stage);
        lineup.setTeam(team);
        lineup.setSubmittedAt(OffsetDateTime.now());
        captainDraftLineupRepository.save(lineup);
        lineups.add(lineup);
        return toResponse(draft, participants, entries, lineups, viewerUserId);
    }

    /**
     * Changes how a running draft's sets are filled in. To the captains: both captains linked to an
     * account, and only before any set has a result, as a result shown to everyone cannot be hidden
     * again. Back to the host: not while one captain alone has handed a stage in, which would show
     * their line-up to the other team before theirs is in; the line-ups handed in are let go, and
     * every player is shown to everyone.
     */
    @Transactional
    public CaptainDraftResponse changeEntryMode(
        Long groupId,
        Long draftId,
        CaptainDraftEntryModeRequest request,
        UUID viewerUserId
    ) {
        if (request == null || safeTrim(request.entryMode()).isEmpty()) {
            throw new IllegalArgumentException("entryMode is required");
        }
        String entryMode = resolveEntryMode(request.entryMode());
        CaptainDraft draft = loadDraft(groupId, draftId);
        List<CaptainDraftParticipant> participants =
            captainDraftParticipantRepository.findByDraftIdWithPlayer(draft.getId());
        List<CaptainDraftEntry> entries = captainDraftEntryRepository.findByDraftIdWithPlayers(draft.getId());
        if (!entryMode.equals(isRunByCaptains(draft) ? ENTRY_MODE_CAPTAINS : ENTRY_MODE_HOST)) {
            if (ENTRY_MODE_CAPTAINS.equals(entryMode)) {
                requireLinkedCaptains(draft.getHomeCaptain(), draft.getAwayCaptain());
                if (entries.stream().anyMatch(entry -> normalizeWinnerTeam(entry.getWinnerTeam()) != null)) {
                    throw new IllegalStateException("The captains cannot take the line-ups over once a set has a result");
                }
            } else {
                if (teamsInByStage(lineupsOf(draft)).values().stream().anyMatch(teams -> teams.size() == 1)) {
                    throw new IllegalStateException("One captain alone has handed a line-up in; it would be shown to the other team");
                }
                captainDraftLineupRepository.deleteByDraftId(draft.getId());
            }
            draft.setEntryMode(entryMode);
            captainDraftRepository.save(draft);
        }
        return toResponse(draft, participants, entries, lineupsOf(draft), viewerUserId);
    }

    /**
     * Adds the ace decider (에이스 결정전) to a draft whose regular rounds have all been played and
     * ended level on rounds won: one more round of a single set, teamSize a side (1 if left out),
     * won like any round. It is offered, not forced: whoever runs the draft decides to play it. A
     * draft has one at most; in a draft run by its captains, each captain hands its line-up in once.
     */
    @Transactional
    public CaptainDraftResponse addAceDecider(
        Long groupId,
        Long draftId,
        CaptainDraftAceRequest request,
        UUID viewerUserId
    ) {
        CaptainDraft draft = loadDraft(groupId, draftId);
        List<CaptainDraftParticipant> participants =
            captainDraftParticipantRepository.findByDraftIdWithPlayer(draft.getId());
        requireActiveDraftParticipants(participants);
        List<CaptainDraftEntry> entries = new ArrayList<>(captainDraftEntryRepository.findByDraftIdWithPlayers(draft.getId()));
        if (entries.stream().anyMatch(CaptainDraftEntry::isAceDecider)) {
            throw new IllegalStateException("This draft has an ace decider already");
        }
        if (entries.isEmpty() || entries.stream().anyMatch(entry -> normalizeWinnerTeam(entry.getWinnerTeam()) == null)) {
            throw new IllegalStateException("Every set needs a result before an ace decider");
        }
        RoundTally tally = tally(entries);
        if (tally.homeRoundWins() != tally.awayRoundWins()) {
            throw new IllegalStateException("The rounds did not end level");
        }
        int teamSize = request == null || request.teamSize() == null ? 1 : request.teamSize();
        int smallerTeam = Math.min(teamPlayerIds(participants, TEAM_HOME).size(), teamPlayerIds(participants, TEAM_AWAY).size());
        if (teamSize < 1 || teamSize > 3 || teamSize > smallerTeam) {
            throw new IllegalArgumentException("An ace decider is 1:1, 2:2 or 3:3 within the teams");
        }

        CaptainDraftEntry ace = new CaptainDraftEntry();
        ace.setDraft(draft);
        ace.setRoundNumber(entries.stream().mapToInt(CaptainDraftEntry::getRoundNumber).max().orElse(0) + 1);
        ace.setSetNumber(1);
        ace.setTeamSize(teamSize);
        ace.setRoundCode(setComposition(DEFAULT_ROUND_CODES.get(0), teamSize));
        ace.setAceDecider(true);
        captainDraftEntryRepository.save(ace);
        entries.add(ace);
        return toResponse(draft, participants, entries, lineupsOf(draft), viewerUserId);
    }

    private boolean isRunByCaptains(CaptainDraft draft) {
        return ENTRY_MODE_CAPTAINS.equals(safeTrim(draft.getEntryMode()));
    }

    // MAIN: the regular rounds, handed in together; ACE: the ace decider, handed in once it is played.
    private String stageOf(CaptainDraftEntry entry) {
        return entry.isAceDecider() ? STAGE_ACE : STAGE_MAIN;
    }

    private String resolveStage(String stage) {
        String normalized = safeTrim(stage).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || STAGE_MAIN.equals(normalized)) {
            return STAGE_MAIN;
        }
        if (STAGE_ACE.equals(normalized)) {
            return STAGE_ACE;
        }
        throw new IllegalArgumentException("stage must be MAIN or ACE");
    }

    // A captain hands a line-up in signed in as themselves, so their player needs a linked account.
    private void requireLinkedCaptains(Player homeCaptain, Player awayCaptain) {
        List<String> unlinked = List.of(homeCaptain, awayCaptain)
            .stream()
            .filter(captain -> captain.getAuthUserId() == null)
            .map(PlayerIdentityPolicy::responseNickname)
            .toList();
        if (!unlinked.isEmpty()) {
            // Shown on the page as it is, so in the page's language.
            throw new IllegalArgumentException(
                "사이트 계정과 연결되지 않은 감독이 있어 감독 제출 방식으로 진행할 수 없습니다: " + String.join(", ", unlinked)
            );
        }
    }

    // A draft run by the host has no line-ups to read.
    private List<CaptainDraftLineup> lineupsOf(CaptainDraft draft) {
        return isRunByCaptains(draft)
            ? new ArrayList<>(captainDraftLineupRepository.findByDraftId(draft.getId()))
            : new ArrayList<>();
    }

    private Map<String, Set<String>> teamsInByStage(List<CaptainDraftLineup> lineups) {
        Map<String, Set<String>> teams = new HashMap<>();
        for (CaptainDraftLineup lineup : lineups) {
            teams.computeIfAbsent(safeTrim(lineup.getStage()), ignored -> new HashSet<>()).add(normalizeTeam(lineup.getTeam()));
        }
        return teams;
    }

    // The participant whose player is linked to the verified account asking; none for anyone else.
    private CaptainDraftParticipant viewerParticipant(List<CaptainDraftParticipant> participants, UUID viewerUserId) {
        if (viewerUserId == null) {
            return null;
        }
        return participants
            .stream()
            .filter(participant -> participant.getPlayer() != null
                && !PlayerIdentityPolicy.isIdentityHidden(participant.getPlayer())
                && viewerUserId.equals(participant.getPlayer().getAuthUserId()))
            .findFirst()
            .orElse(null);
    }

    private String resolveEntryMode(String entryMode) {
        String normalized = safeTrim(entryMode).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return ENTRY_MODE_HOST;
        }
        if (!ENTRY_MODE_HOST.equals(normalized) && !ENTRY_MODE_CAPTAINS.equals(normalized)) {
            throw new IllegalArgumentException("entryMode must be HOST or CAPTAINS");
        }
        return normalized;
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

    // Whether saving these values would change a set: its players slot by slot, races, tier note or result.
    private boolean changesSet(
        CaptainDraftEntry entry,
        List<Player> home,
        List<Player> away,
        String raceCode,
        String requiredTier,
        String winnerTeam
    ) {
        return !slotPlayerIds(entry.sidePlayers(TEAM_HOME)).equals(slotPlayerIds(home))
            || !slotPlayerIds(entry.sidePlayers(TEAM_AWAY)).equals(slotPlayerIds(away))
            || !Objects.equals(entry.getRoundCode(), raceCode)
            || !Objects.equals(emptyToNull(entry.getRequiredTier()), requiredTier)
            || !Objects.equals(normalizeWinnerTeam(entry.getWinnerTeam()), winnerTeam);
    }

    private List<Long> slotPlayerIds(List<Player> side) {
        return side.stream().map(player -> player == null ? null : player.getId()).toList();
    }

    private String emptyToNull(String value) {
        String trimmed = safeTrim(value);
        return trimmed.isEmpty() ? null : trimmed;
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

    private record RoundTally(int homeRoundWins, int awayRoundWins, int homeSetWins, int awaySetWins) {
    }

    // A round goes to the team that won more of its sets once every set has a result; the ace
    // decider is a round of its own.
    private RoundTally tally(List<CaptainDraftEntry> entries) {
        Map<Integer, List<CaptainDraftEntry>> rounds = entries
            .stream()
            .collect(Collectors.groupingBy(CaptainDraftEntry::getRoundNumber, TreeMap::new, Collectors.toList()));
        int homeRoundWins = 0;
        int awayRoundWins = 0;
        int homeSetWins = 0;
        int awaySetWins = 0;
        for (List<CaptainDraftEntry> sets : rounds.values()) {
            int home = 0;
            int away = 0;
            for (CaptainDraftEntry set : sets) {
                String winner = normalizeWinnerTeam(set.getWinnerTeam());
                if (TEAM_HOME.equals(winner)) {
                    home++;
                } else if (TEAM_AWAY.equals(winner)) {
                    away++;
                }
            }
            homeSetWins += home;
            awaySetWins += away;
            if (home + away == sets.size() && home != away) {
                if (home > away) {
                    homeRoundWins++;
                } else {
                    awayRoundWins++;
                }
            }
        }
        return new RoundTally(homeRoundWins, awayRoundWins, homeSetWins, awaySetWins);
    }

    private CaptainDraftSummaryResponse summarize(CaptainDraft draft, List<CaptainDraftEntry> entries, long participants) {
        RoundTally tally = tally(entries);
        long regularRounds = entries
            .stream()
            .filter(entry -> !entry.isAceDecider())
            .map(CaptainDraftEntry::getRoundNumber)
            .distinct()
            .count();
        return new CaptainDraftSummaryResponse(
            draft.getId(),
            draft.getTitle(),
            draft.getCreatedAt(),
            safeTrim(draft.getStatus()),
            responseNickname(draft.getHomeCaptain()),
            responseNickname(draft.getAwayCaptain()),
            (int) participants,
            (int) regularRounds,
            safeInt(draft.getSetsPerRound()),
            tally.homeRoundWins(),
            tally.awayRoundWins(),
            tally.homeSetWins(),
            tally.awaySetWins(),
            tally.homeSetWins() + tally.awaySetWins(),
            entries.size(),
            draft.getCreatedByNickname(),
            entries.stream().anyMatch(CaptainDraftEntry::isAceDecider)
        );
    }

    private String actorEmailOrNull(String email) {
        String normalized = safeTrim(email).toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    // As matches keep a recorder's nickname: at most 100 characters, none when there is none.
    private String actorNicknameOrNull(String nickname) {
        String normalized = safeTrim(nickname);
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() > MAX_ACTOR_NICKNAME_LENGTH ? normalized.substring(0, MAX_ACTOR_NICKNAME_LENGTH) : normalized;
    }

    private CaptainDraft loadDraft(Long groupId, Long draftId) {
        return captainDraftRepository.findByIdAndGroup_Id(draftId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Draft not found: " + draftId));
    }

    // A title longer than its column is refused here (400), not left to fail in the database (500).
    private String resolveTitle(String title) {
        String normalized = safeTrim(title);
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title must be at most " + MAX_TITLE_LENGTH + " characters");
        }
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

    /**
     * The draft for one reader. In a draft run by its captains, a team's players of a stage (the
     * regular rounds, or the ace decider) not in for both teams yet are left out (every slot null, the
     * side marked hidden) unless the reader's player is on that team; everything else is the same
     * for everyone.
     */
    private CaptainDraftResponse toResponse(
        CaptainDraft draft,
        List<CaptainDraftParticipant> participants,
        List<CaptainDraftEntry> entries,
        List<CaptainDraftLineup> lineups,
        UUID viewerUserId
    ) {
        boolean byCaptains = isRunByCaptains(draft);
        CaptainDraftParticipant viewer = viewerParticipant(participants, viewerUserId);
        String viewerTeam = viewer == null ? TEAM_UNASSIGNED : normalizeTeam(viewer.getTeam());
        Map<String, Set<String>> teamsIn = teamsInByStage(lineups);
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
            .map(entry -> {
                boolean shown = !byCaptains || teamsIn.getOrDefault(stageOf(entry), Set.of()).size() == 2;
                boolean homeHidden = !shown && !TEAM_HOME.equals(viewerTeam);
                boolean awayHidden = !shown && !TEAM_AWAY.equals(viewerTeam);
                return new CaptainDraftEntryResponse(
                    safeInt(entry.getRoundNumber()),
                    safeTrim(entry.getRoundCode()),
                    safeInt(entry.getSetNumber()),
                    entry.sidePlayers(TEAM_HOME).size(),
                    entry.getRequiredTier(),
                    homeHidden ? hiddenSide(entry) : sideResponse(entry, TEAM_HOME),
                    awayHidden ? hiddenSide(entry) : sideResponse(entry, TEAM_AWAY),
                    normalizeWinnerTeam(entry.getWinnerTeam()),
                    entry.getResultRecordedByNickname(),
                    entry.getResultRecordedAt(),
                    homeHidden,
                    awayHidden,
                    entry.isAceDecider()
                );
            })
            .toList();

        // The regular rounds always, the ace decider once it is played.
        List<CaptainDraftLineupResponse> lineupResponses = new ArrayList<>();
        if (byCaptains) {
            boolean acePlayed = entries.stream().anyMatch(CaptainDraftEntry::isAceDecider);
            for (String stage : acePlayed ? List.of(STAGE_MAIN, STAGE_ACE) : List.of(STAGE_MAIN)) {
                Set<String> in = teamsIn.getOrDefault(stage, Set.of());
                lineupResponses.add(new CaptainDraftLineupResponse(
                    stage, in.contains(TEAM_HOME), in.contains(TEAM_AWAY), in.size() == 2
                ));
            }
        }

        return new CaptainDraftResponse(
            draft.getId(),
            draft.getGroup().getId(),
            draft.getTitle(),
            draft.getCreatedAt(),
            draft.getCreatedByNickname(),
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
            entryResponses,
            byCaptains ? ENTRY_MODE_CAPTAINS : ENTRY_MODE_HOST,
            TEAM_UNASSIGNED.equals(viewerTeam) ? null : viewerTeam,
            viewer != null && viewer.isCaptain(),
            lineupResponses
        );
    }

    // A side the reader may not see yet: as many empty slots as the set has, and nothing else.
    private List<CaptainDraftEntryPlayerResponse> hiddenSide(CaptainDraftEntry entry) {
        int size = entry.getTeamSize() == null || entry.getTeamSize() < 1 ? 1 : entry.getTeamSize();
        List<CaptainDraftEntryPlayerResponse> empty = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            empty.add(null);
        }
        return empty;
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
