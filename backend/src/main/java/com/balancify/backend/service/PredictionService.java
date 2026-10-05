package com.balancify.backend.service;

import com.balancify.backend.api.prediction.dto.PredictionBoardResponse;
import com.balancify.backend.api.prediction.dto.PredictionMatchResponse;
import com.balancify.backend.api.prediction.dto.PredictionPlayerResponse;
import com.balancify.backend.api.prediction.dto.PredictionStatsResponse;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.MatchPrediction;
import com.balancify.backend.domain.MatchStatus;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchPredictionRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.service.exception.MatchConflictException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Free win predictions. Picks are taken while a match waits for its result, until a few minutes
 * after it was set up, an admin closes them, or the result comes in. A correct pick earns points;
 * nothing is staked. Points follow every change to the result: recorded, corrected or deleted.
 */
@Service
public class PredictionService {

    static final String STATE_OPEN = "OPEN";
    static final String STATE_CLOSED = "CLOSED";
    static final String STATE_RESOLVED = "RESOLVED";
    private static final String TEAM_HOME = "HOME";
    private static final String TEAM_AWAY = "AWAY";
    private static final int HISTORY_LIMIT = 20;
    // Matches still waiting for a result after this long are treated as abandoned.
    private static final long AWAITING_LOOKBACK_HOURS = 6;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MatchPredictionRepository matchPredictionRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final PointService pointService;
    private final AccessControlService accessControlService;
    private final OperationAuditLogService operationAuditLogService;
    private final boolean membersEnabled;
    private final int windowMinutes;
    private final Clock clock;

    @Autowired
    public PredictionService(
        MatchPredictionRepository matchPredictionRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        PointService pointService,
        AccessControlService accessControlService,
        OperationAuditLogService operationAuditLogService,
        @Value("${balancify.predictions.members-enabled:false}") boolean membersEnabled,
        @Value("${balancify.predictions.window-minutes:3}") int windowMinutes
    ) {
        this(
            matchPredictionRepository,
            matchRepository,
            matchParticipantRepository,
            pointService,
            accessControlService,
            operationAuditLogService,
            membersEnabled,
            windowMinutes,
            Clock.system(KST)
        );
    }

    PredictionService(
        MatchPredictionRepository matchPredictionRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        PointService pointService,
        AccessControlService accessControlService,
        OperationAuditLogService operationAuditLogService,
        boolean membersEnabled,
        int windowMinutes,
        Clock clock
    ) {
        this.matchPredictionRepository = matchPredictionRepository;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.pointService = pointService;
        this.accessControlService = accessControlService;
        this.operationAuditLogService = operationAuditLogService;
        this.membersEnabled = membersEnabled;
        this.windowMinutes = Math.max(1, windowMinutes);
        this.clock = clock;
    }

    /** Admins only while predictions are tried out; every member once balancify.predictions.members-enabled is on. */
    public boolean canPredict(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            return false;
        }
        return membersEnabled
            ? accessControlService.isServiceAccessAllowed(normalizedEmail)
            : accessControlService.isAdminEmail(normalizedEmail);
    }

    /** Records or changes a pick. The match row lock keeps a pick from slipping in beside its result. */
    @Transactional
    public PredictionMatchResponse predict(Long groupId, Long matchId, String email, String userId, String team) {
        String normalizedEmail = normalizeEmail(email);
        String pickedTeam = normalizeTeam(team);
        if (pickedTeam == null) {
            throw new IllegalArgumentException("team must be HOME or AWAY");
        }
        Match match = matchRepository.findByIdForUpdate(matchId)
            .filter(found -> belongsTo(found, groupId))
            .orElseThrow(() -> new NoSuchElementException("Match not found: " + matchId));
        OffsetDateTime now = now();
        if (!isOpen(match, now)) {
            throw new MatchConflictException("예측이 마감된 경기입니다.");
        }
        List<MatchParticipant> participants = matchParticipantRepository.findByMatchIdWithPlayerAndMatch(matchId);
        if (ownMatch(participants, normalizedEmail, userId)) {
            throw new IllegalArgumentException("본인이 뛰는 경기는 예측할 수 없습니다.");
        }

        MatchPrediction prediction = matchPredictionRepository.findByMatchIdAndPredictorEmail(matchId, normalizedEmail)
            .orElseGet(() -> {
                MatchPrediction created = new MatchPrediction();
                created.setMatchId(matchId);
                created.setPredictorEmail(normalizedEmail);
                return created;
            });
        prediction.setPredictedTeam(pickedTeam);
        matchPredictionRepository.save(prediction);
        return matchResponse(match, participants, List.of(prediction), normalizedEmail, false, now, Map.of());
    }

    /** Closes picks for a match ahead of time; once its result is in they are closed anyway. */
    @Transactional
    public void close(Long groupId, Long matchId, String actorEmail, String actorNickname) {
        Match match = matchRepository.findByIdForUpdate(matchId)
            .filter(found -> belongsTo(found, groupId))
            .orElseThrow(() -> new NoSuchElementException("Match not found: " + matchId));
        if (!isOpen(match, now())) {
            return;
        }
        match.setPredictionsClosedAt(now());
        matchRepository.save(match);
        operationAuditLogService.recordPredictionsClosed(actorEmail, actorNickname, matchId, groupId);
    }

    /**
     * Brings every pick on a match in line with its current result. Whoever recorded or last
     * changed the result gets nothing for their own pick on it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void settle(Match match) {
        if (match == null || match.getId() == null) {
            return;
        }
        String winner = normalizeTeam(match.getWinningTeam());
        String recorder = normalizeEmail(match.getResultRecordedByEmail());
        for (MatchPrediction prediction : matchPredictionRepository.findByMatchIdOrderByPredictorEmailAsc(match.getId())) {
            boolean hit = winner != null
                && winner.equals(prediction.getPredictedTeam())
                && !prediction.getPredictorEmail().equals(recorder);
            pointService.syncPredictionPoint(prediction.getPredictorEmail(), match.getId(), hit);
        }
    }

    /** Takes back every prediction point of a match that is about to be deleted. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(Long matchId) {
        if (matchId == null) {
            return;
        }
        for (MatchPrediction prediction : matchPredictionRepository.findByMatchIdOrderByPredictorEmailAsc(matchId)) {
            pointService.syncPredictionPoint(prediction.getPredictorEmail(), matchId, false);
        }
    }

    @Transactional(readOnly = true)
    public PredictionBoardResponse board(Long groupId, String email, String userId) {
        String normalizedEmail = normalizeEmail(email);
        OffsetDateTime now = now();
        List<Match> awaiting = matchRepository.findAwaitingResultSince(groupId, now.minusHours(AWAITING_LOOKBACK_HOURS));
        List<MatchPrediction> myHistory = matchPredictionRepository.findResolvedByPredictor(
            normalizedEmail,
            PageRequest.of(0, HISTORY_LIMIT)
        );
        List<Match> historyMatches = new ArrayList<>();
        matchRepository.findAllById(myHistory.stream().map(MatchPrediction::getMatchId).toList())
            .forEach(historyMatches::add);
        historyMatches.removeIf(match -> !belongsTo(match, groupId));
        historyMatches.sort((left, right) -> Long.compare(right.getId(), left.getId()));

        Set<Long> matchIds = new LinkedHashSet<>();
        awaiting.forEach(match -> matchIds.add(match.getId()));
        historyMatches.forEach(match -> matchIds.add(match.getId()));
        Map<Long, List<MatchParticipant>> participantsByMatch = new HashMap<>();
        Map<Long, List<MatchPrediction>> predictionsByMatch = new HashMap<>();
        if (!matchIds.isEmpty()) {
            List<Long> ids = List.copyOf(matchIds);
            for (MatchParticipant participant : matchParticipantRepository.findByMatchIdInWithPlayerAndMatch(ids)) {
                participantsByMatch.computeIfAbsent(participant.getMatch().getId(), ignored -> new ArrayList<>()).add(participant);
            }
            for (MatchPrediction prediction : matchPredictionRepository.findByMatchIdIn(ids)) {
                predictionsByMatch.computeIfAbsent(prediction.getMatchId(), ignored -> new ArrayList<>()).add(prediction);
            }
        }

        // Who picked which side (once picks are closed) and who set each match up go out by nickname;
        // emails stay on the server.
        Set<String> namedEmails = new LinkedHashSet<>();
        predictionsByMatch.values().forEach(predictions ->
            predictions.forEach(prediction -> namedEmails.add(normalizeEmail(prediction.getPredictorEmail()))));
        awaiting.forEach(match -> namedEmails.add(normalizeEmail(match.getCreatedByEmail())));
        historyMatches.forEach(match -> namedEmails.add(normalizeEmail(match.getCreatedByEmail())));
        namedEmails.remove("");
        Map<String, String> nicknames = accessControlService.resolveDisplayNicknames(List.copyOf(namedEmails));

        List<PredictionMatchResponse> open = new ArrayList<>();
        List<PredictionMatchResponse> closed = new ArrayList<>();
        for (Match match : awaiting) {
            PredictionMatchResponse response = matchResponse(
                match,
                participantsByMatch.getOrDefault(match.getId(), List.of()),
                predictionsByMatch.getOrDefault(match.getId(), List.of()),
                normalizedEmail,
                ownMatch(participantsByMatch.getOrDefault(match.getId(), List.of()), normalizedEmail, userId),
                now,
                nicknames
            );
            (STATE_OPEN.equals(response.state()) ? open : closed).add(response);
        }
        List<PredictionMatchResponse> history = historyMatches.stream()
            .map(match -> matchResponse(
                match,
                participantsByMatch.getOrDefault(match.getId(), List.of()),
                predictionsByMatch.getOrDefault(match.getId(), List.of()),
                normalizedEmail,
                false,
                now,
                nicknames
            ))
            .toList();

        MatchPredictionRepository.PredictionRecord record = matchPredictionRepository.summarizeResolved(normalizedEmail);
        return new PredictionBoardResponse(
            now,
            windowMinutes,
            open,
            closed,
            history,
            new PredictionStatsResponse(
                record == null || record.getTotal() == null ? 0 : record.getTotal(),
                record == null || record.getHits() == null ? 0 : record.getHits()
            )
        );
    }

    private PredictionMatchResponse matchResponse(
        Match match,
        List<MatchParticipant> participants,
        List<MatchPrediction> predictions,
        String email,
        boolean ownMatch,
        OffsetDateTime now,
        Map<String, String> nicknames
    ) {
        String winner = normalizeTeam(match.getWinningTeam());
        String state = winner != null ? STATE_RESOLVED : isOpen(match, now) ? STATE_OPEN : STATE_CLOSED;
        String myPick = predictions.stream()
            .filter(prediction -> prediction.getPredictorEmail().equals(email))
            .map(MatchPrediction::getPredictedTeam)
            .findFirst()
            .orElse(null);
        boolean countsVisible = !STATE_OPEN.equals(state);
        int homePicks = (int) predictions.stream().filter(prediction -> TEAM_HOME.equals(prediction.getPredictedTeam())).count();
        int awayPicks = predictions.size() - homePicks;
        return new PredictionMatchResponse(
            match.getId(),
            state,
            match.getRaceComposition(),
            match.getSeriesGameNumber(),
            match.getCreatedAt(),
            closesAt(match),
            players(participants, TEAM_HOME),
            players(participants, TEAM_AWAY),
            myPick,
            ownMatch,
            countsVisible ? homePicks : null,
            countsVisible ? awayPicks : null,
            countsVisible ? pickers(predictions, TEAM_HOME, nicknames) : null,
            countsVisible ? pickers(predictions, TEAM_AWAY, nicknames) : null,
            winner,
            winner == null || myPick == null ? null : winner.equals(myPick),
            winner != null && email.equals(normalizeEmail(match.getResultRecordedByEmail())),
            nicknames.get(normalizeEmail(match.getCreatedByEmail()))
        );
    }

    // Nicknames in order, those without one (null) last, so the list reads the same on every load.
    private List<String> pickers(List<MatchPrediction> predictions, String team, Map<String, String> nicknames) {
        return predictions.stream()
            .filter(prediction -> team.equals(prediction.getPredictedTeam()))
            .map(prediction -> nicknames.get(normalizeEmail(prediction.getPredictorEmail())))
            .sorted(Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private List<PredictionPlayerResponse> players(List<MatchParticipant> participants, String team) {
        return participants.stream()
            .filter(participant -> team.equals(normalizeTeam(participant.getTeam())))
            .sorted((left, right) -> Long.compare(
                left.getId() == null ? 0 : left.getId(),
                right.getId() == null ? 0 : right.getId()
            ))
            .map(participant -> new PredictionPlayerResponse(
                PlayerIdentityPolicy.responseNickname(participant.getPlayer()),
                participant.getAssignedRace()
            ))
            .toList();
    }

    boolean isOpen(Match match, OffsetDateTime now) {
        return match.getStatus() == MatchStatus.CONFIRMED
            && match.getWinningTeam() == null
            && match.getPredictionsClosedAt() == null
            && match.getCreatedAt() != null
            && now.isBefore(match.getCreatedAt().plusMinutes(windowMinutes));
    }

    private OffsetDateTime closesAt(Match match) {
        OffsetDateTime natural = match.getCreatedAt() == null ? null : match.getCreatedAt().plusMinutes(windowMinutes);
        OffsetDateTime closedAt = match.getPredictionsClosedAt();
        if (closedAt != null && (natural == null || closedAt.isBefore(natural))) {
            return closedAt;
        }
        return natural;
    }

    private boolean ownMatch(List<MatchParticipant> participants, String email, String userId) {
        return OwnPlayerPolicy.findOwn(participants, userId, accessControlService.resolveDisplayNickname(email)) != null;
    }

    private boolean belongsTo(Match match, Long groupId) {
        return match.getGroup() != null && Objects.equals(match.getGroup().getId(), groupId);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static String normalizeTeam(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return TEAM_HOME.equals(normalized) || TEAM_AWAY.equals(normalized) ? normalized : null;
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
