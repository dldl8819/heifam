package com.balancify.backend.service;

import com.balancify.backend.api.points.dto.MatchConfirmationListResponse;
import com.balancify.backend.api.points.dto.MatchConfirmationPlayerResponse;
import com.balancify.backend.api.points.dto.MatchConfirmationResponse;
import com.balancify.backend.config.PointProperties;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.MatchSource;
import com.balancify.backend.domain.RankedMatchPolicy;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.service.exception.MatchConfirmationForbiddenException;
import com.balancify.backend.service.exception.MatchConflictException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Players confirm the result of a match they played and earn a point for it, once per match,
 * within a window after the result came in. Predictions cannot be made on one's own match, so
 * this is how the players earn from it. The matches are the ones whose result entry earns a
 * point: balanced (multi-balance series games included), 3v3, with a result.
 */
@Service
public class MatchConfirmationService {

    private static final String TEAM_HOME = "HOME";
    private static final String TEAM_AWAY = "AWAY";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final PointService pointService;
    private final AccessControlService accessControlService;
    private final PointProperties pointProperties;
    private final Clock clock;

    @Autowired
    public MatchConfirmationService(
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        PointService pointService,
        AccessControlService accessControlService,
        PointProperties pointProperties
    ) {
        this(
            matchRepository,
            matchParticipantRepository,
            pointService,
            accessControlService,
            pointProperties,
            Clock.system(KST)
        );
    }

    MatchConfirmationService(
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        PointService pointService,
        AccessControlService accessControlService,
        PointProperties pointProperties,
        Clock clock
    ) {
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.pointService = pointService;
        this.accessControlService = accessControlService;
        this.pointProperties = pointProperties;
        this.clock = clock;
    }

    /** The matches the person played whose results they can still confirm, newest result first. */
    @Transactional(readOnly = true)
    public MatchConfirmationListResponse list(Long groupId, String email, String userId) {
        String normalizedEmail = normalizeEmail(email);
        OffsetDateTime now = now();
        List<Match> recorded = matchRepository.findBalancedResultsRecordedSince(
            groupId,
            now.minusHours(pointProperties.getMatchConfirmWindowHours())
        );
        Map<Long, List<MatchParticipant>> participantsByMatch = new HashMap<>();
        if (!recorded.isEmpty()) {
            for (MatchParticipant participant : matchParticipantRepository.findByMatchIdInWithPlayerAndMatch(
                recorded.stream().map(Match::getId).toList()
            )) {
                participantsByMatch.computeIfAbsent(participant.getMatch().getId(), ignored -> new ArrayList<>()).add(participant);
            }
        }

        String accountNickname = accessControlService.resolveDisplayNickname(normalizedEmail);
        List<Match> mine = new ArrayList<>();
        Map<Long, String> myTeams = new HashMap<>();
        for (Match match : recorded) {
            List<MatchParticipant> participants = participantsByMatch.getOrDefault(match.getId(), List.of());
            MatchParticipant own = OwnPlayerPolicy.findOwn(participants, userId, accountNickname);
            if (own == null || !isEligible(match, participants) || !now.isBefore(confirmDeadline(match))) {
                continue;
            }
            mine.add(match);
            myTeams.put(match.getId(), normalizeTeam(own.getTeam()));
        }

        PointService.MatchConfirmState state = pointService.getMatchConfirmState(
            normalizedEmail,
            mine.stream().map(Match::getId).toList()
        );
        List<MatchConfirmationResponse> matches = mine.stream()
            .map(match -> {
                List<MatchParticipant> participants = participantsByMatch.getOrDefault(match.getId(), List.of());
                return new MatchConfirmationResponse(
                    match.getId(),
                    match.getRaceComposition(),
                    match.getSeriesGameNumber(),
                    match.getResultRecordedAt(),
                    confirmDeadline(match),
                    players(participants, TEAM_HOME),
                    players(participants, TEAM_AWAY),
                    normalizeTeam(match.getWinningTeam()),
                    myTeams.get(match.getId()),
                    state.confirmedMatchIds().contains(match.getId())
                );
            })
            .toList();
        return new MatchConfirmationListResponse(
            matches,
            state.confirmedToday(),
            pointProperties.getMatchConfirmDailyCap(),
            pointProperties.getMatchConfirm(),
            pointProperties.getMatchConfirmWindowHours()
        );
    }

    /**
     * Confirms one result for its point. Confirming again is a no-op, even after the window. The
     * match row lock keeps a confirmation from slipping in while the match is being deleted, which
     * takes the same lock before it takes the points back.
     */
    @Transactional
    public MatchConfirmationListResponse confirm(Long groupId, Long matchId, String email, String userId) {
        String normalizedEmail = normalizeEmail(email);
        Match match = matchRepository.findByIdForUpdate(matchId)
            .filter(found -> belongsTo(found, groupId))
            .orElseThrow(() -> new NoSuchElementException("Match not found: " + matchId));
        List<MatchParticipant> participants = matchParticipantRepository.findByMatchIdWithPlayerAndMatch(matchId);
        if (!isEligible(match, participants)) {
            throw new IllegalArgumentException("결과 확인 포인트를 받을 수 없는 경기입니다.");
        }
        String accountNickname = accessControlService.resolveDisplayNickname(normalizedEmail);
        if (OwnPlayerPolicy.findOwn(participants, userId, accountNickname) == null) {
            throw new MatchConfirmationForbiddenException("본인이 뛴 경기만 확인할 수 있습니다.");
        }

        if (!now().isBefore(confirmDeadline(match))) {
            if (!pointService.hasConfirmedMatch(normalizedEmail, matchId)) {
                throw new IllegalArgumentException("결과 확인 기한이 지난 경기입니다.");
            }
            return list(groupId, normalizedEmail, userId);
        }
        switch (pointService.grantMatchConfirmPoint(normalizedEmail, matchId)) {
            case DAILY_CAP_REACHED -> throw new MatchConflictException("오늘 받을 수 있는 경기 결과 확인 포인트를 모두 받았습니다.");
            case NOT_ALLOWED -> throw new MatchConfirmationForbiddenException("포인트를 쓸 수 없는 계정입니다.");
            case CONFIRMED, ALREADY_CONFIRMED -> {
            }
        }
        return list(groupId, normalizedEmail, userId);
    }

    static boolean isEligible(Match match, List<MatchParticipant> participants) {
        if (match.getSource() != MatchSource.BALANCED
            || normalizeTeam(match.getWinningTeam()) == null
            || match.getResultRecordedAt() == null) {
            return false;
        }
        long home = participants.stream().filter(participant -> TEAM_HOME.equals(normalizeTeam(participant.getTeam()))).count();
        long away = participants.stream().filter(participant -> TEAM_AWAY.equals(normalizeTeam(participant.getTeam()))).count();
        return home == away && RankedMatchPolicy.affectsRating((int) home);
    }

    private OffsetDateTime confirmDeadline(Match match) {
        return match.getResultRecordedAt().plusHours(pointProperties.getMatchConfirmWindowHours());
    }

    private List<MatchConfirmationPlayerResponse> players(List<MatchParticipant> participants, String team) {
        return participants.stream()
            .filter(participant -> team.equals(normalizeTeam(participant.getTeam())))
            .sorted((left, right) -> Long.compare(
                left.getId() == null ? 0 : left.getId(),
                right.getId() == null ? 0 : right.getId()
            ))
            .map(participant -> new MatchConfirmationPlayerResponse(
                PlayerIdentityPolicy.responseNickname(participant.getPlayer()),
                participant.getAssignedRace()
            ))
            .toList();
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
