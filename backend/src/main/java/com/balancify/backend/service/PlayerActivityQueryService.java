package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupDormantPlayerResponse;
import com.balancify.backend.api.group.dto.GroupPlayerLastParticipationResponse;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchParticipantRepository.PlayerLastPlayedAtProjection;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.repository.PlayerRepository.PlayerActivityCandidateProjection;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerActivityQueryService {

    private final PlayerRepository playerRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final boolean enabled;
    private final int inactiveDays;
    private final Clock clock;

    @Autowired
    public PlayerActivityQueryService(
        PlayerRepository playerRepository,
        MatchParticipantRepository matchParticipantRepository,
        @Value("${balancify.rank.dormancy.enabled:true}") boolean enabled,
        @Value("${balancify.rank.dormancy.inactive-days:15}") int inactiveDays
    ) {
        this(playerRepository, matchParticipantRepository, enabled, inactiveDays, Clock.systemUTC());
    }

    PlayerActivityQueryService(
        PlayerRepository playerRepository,
        MatchParticipantRepository matchParticipantRepository,
        boolean enabled,
        int inactiveDays,
        Clock clock
    ) {
        this.playerRepository = playerRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.enabled = enabled;
        this.inactiveDays = Math.max(0, inactiveDays);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GroupDormantPlayerResponse> getDormantPlayers(Long groupId) {
        if (groupId == null || !enabled || inactiveDays <= 0) {
            return List.of();
        }

        List<PlayerActivityCandidateProjection> candidates =
            playerRepository.findActivityCandidatesByGroupId(groupId);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, OffsetDateTime> lastPlayedAtByPlayerId = resolveLastPlayedAtByPlayerId(groupId);
        OffsetDateTime now = OffsetDateTime.now(clock);

        return candidates.stream()
            .filter(candidate -> candidate.getPlayerId() != null)
            .filter(candidate -> isDormant(
                lastPlayedAtByPlayerId.get(candidate.getPlayerId()),
                candidate.getCreatedAt(),
                now
            ))
            .map(candidate -> new GroupDormantPlayerResponse(
                candidate.getPlayerId(),
                candidate.getNickname()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public GroupPlayerLastParticipationResponse getLastParticipation(Long groupId, Long playerId) {
        boolean visibleActivePlayer = groupId != null
            && playerId != null
            && playerRepository.existsByIdAndGroup_IdAndActiveTrueAndAnonymizedAtIsNull(playerId, groupId);
        if (!visibleActivePlayer) {
            throw new NoSuchElementException("Player not found");
        }

        OffsetDateTime lastPlayedAt = matchParticipantRepository
            .findLastCompletedPlayedAt(groupId, playerId)
            .orElse(null);
        return new GroupPlayerLastParticipationResponse(lastPlayedAt);
    }

    private Map<Long, OffsetDateTime> resolveLastPlayedAtByPlayerId(Long groupId) {
        Map<Long, OffsetDateTime> lastPlayedAtByPlayerId = new HashMap<>();
        for (PlayerLastPlayedAtProjection projection :
            matchParticipantRepository.findLastPlayedAtByGroupId(groupId)) {
            Long playerId = projection.getPlayerId();
            Instant lastPlayedAt = projection.getLastPlayedAt();
            if (playerId == null || lastPlayedAt == null) {
                continue;
            }
            lastPlayedAtByPlayerId.put(
                playerId,
                OffsetDateTime.ofInstant(lastPlayedAt, ZoneOffset.UTC)
            );
        }
        return lastPlayedAtByPlayerId;
    }

    private boolean isDormant(
        OffsetDateTime lastPlayedAt,
        OffsetDateTime createdAt,
        OffsetDateTime now
    ) {
        OffsetDateTime activityReference = lastPlayedAt != null ? lastPlayedAt : createdAt;
        if (activityReference == null || now == null) {
            return false;
        }
        long inactiveDayCount = ChronoUnit.DAYS.between(
            activityReference.toInstant(),
            now.toInstant()
        );
        return inactiveDayCount >= inactiveDays;
    }
}
