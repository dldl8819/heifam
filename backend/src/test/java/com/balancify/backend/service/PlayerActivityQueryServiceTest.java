package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupDormantPlayerResponse;
import com.balancify.backend.domain.PlayerLifecycleStatus;
import com.balancify.backend.api.group.dto.GroupPlayerLastParticipationResponse;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchParticipantRepository.PlayerLastPlayedAtProjection;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.repository.PlayerRepository.PlayerActivityCandidateProjection;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

@ExtendWith(MockitoExtension.class)
class PlayerActivityQueryServiceTest {

    private static final OffsetDateTime FIXED_NOW = OffsetDateTime.parse("2026-08-04T00:00:00Z");

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    private PlayerActivityQueryService service;

    @BeforeEach
    void setUp() {
        service = new PlayerActivityQueryService(
            playerRepository,
            matchParticipantRepository,
            true,
            15,
            Clock.fixed(FIXED_NOW.toInstant(), ZoneOffset.UTC)
        );
    }

    @Test
    void returnsOnlyPlayersWhoseLatestCompletedParticipationReachedDormancyThreshold() {
        when(playerRepository.findActivityCandidatesByGroupId(1L)).thenReturn(List.of(
            candidate(11L, "PLAYER_PLACEHOLDER_1", "2026-06-01T00:00:00Z"),
            candidate(12L, "PLAYER_PLACEHOLDER_2", "2026-06-01T00:00:00Z"),
            candidate(13L, "PLAYER_PLACEHOLDER_3", "2026-06-01T00:00:00Z"),
            candidate(14L, "PLAYER_PLACEHOLDER_4", "2026-07-19T00:00:00Z"),
            candidate(15L, "PLAYER_PLACEHOLDER_5", null)
        ));
        when(matchParticipantRepository.findLastPlayedAtByGroupId(1L)).thenReturn(List.of(
            lastPlayedAt(11L, "2026-07-20T00:00:00Z"),
            lastPlayedAt(12L, "2026-07-21T00:00:00Z"),
            lastPlayedAt(13L, "2026-07-19T00:00:00Z")
        ));

        List<GroupDormantPlayerResponse> response = service.getDormantPlayers(1L);

        assertThat(response)
            .extracting(GroupDormantPlayerResponse::playerId)
            .containsExactly(11L, 13L, 14L);
    }

    @Test
    void disablesDormantRosterWhenConfiguredThresholdIsDisabled() {
        service = new PlayerActivityQueryService(
            playerRepository,
            matchParticipantRepository,
            true,
            0,
            Clock.fixed(FIXED_NOW.toInstant(), ZoneOffset.UTC)
        );

        assertThat(service.getDormantPlayers(1L)).isEmpty();
        verifyNoInteractions(playerRepository, matchParticipantRepository);
    }

    @Test
    void returnsNoDormantPlayersWhenDormancyPolicyIsDisabled() {
        service = new PlayerActivityQueryService(
            playerRepository,
            matchParticipantRepository,
            false,
            15,
            Clock.fixed(FIXED_NOW.toInstant(), ZoneOffset.UTC)
        );

        assertThat(service.getDormantPlayers(1L)).isEmpty();
        verifyNoInteractions(playerRepository, matchParticipantRepository);
    }

    @Test
    void returnsLatestCompletedParticipationTimestamp() {
        OffsetDateTime lastPlayedAt = OffsetDateTime.parse("2026-07-20T12:30:00Z");
        when(playerRepository.existsByIdAndGroup_IdAndActiveTrueAndAnonymizedAtIsNullAndLifecycleStatus(
            11L,
            1L,
            PlayerLifecycleStatus.ACTIVE
        ))
            .thenReturn(true);
        when(matchParticipantRepository.findLastCompletedPlayedAt(1L, 11L))
            .thenReturn(Optional.of(lastPlayedAt));

        GroupPlayerLastParticipationResponse response = service.getLastParticipation(1L, 11L);

        assertThat(response.lastPlayedAt()).isEqualTo(lastPlayedAt);
    }

    @Test
    void returnsNullWhenPlayerHasNoCompletedParticipation() {
        when(playerRepository.existsByIdAndGroup_IdAndActiveTrueAndAnonymizedAtIsNullAndLifecycleStatus(
            11L,
            1L,
            PlayerLifecycleStatus.ACTIVE
        ))
            .thenReturn(true);
        when(matchParticipantRepository.findLastCompletedPlayedAt(1L, 11L))
            .thenReturn(Optional.empty());

        GroupPlayerLastParticipationResponse response = service.getLastParticipation(1L, 11L);

        assertThat(response.lastPlayedAt()).isNull();
    }

    @Test
    void hidesMissingInactiveAnonymizedOrCrossGroupPlayersBehindNotFound() {
        when(playerRepository.existsByIdAndGroup_IdAndActiveTrueAndAnonymizedAtIsNullAndLifecycleStatus(
            11L,
            1L,
            PlayerLifecycleStatus.ACTIVE
        ))
            .thenReturn(false);

        assertThatThrownBy(() -> service.getLastParticipation(1L, 11L))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("Player not found");

        verify(matchParticipantRepository, never()).findLastCompletedPlayedAt(1L, 11L);
    }

    private PlayerActivityCandidateProjection candidate(
        Long playerId,
        String nickname,
        String createdAt
    ) {
        return new PlayerActivityCandidateProjection() {
            @Override
            public Long getPlayerId() {
                return playerId;
            }

            @Override
            public String getNickname() {
                return nickname;
            }

            @Override
            public OffsetDateTime getCreatedAt() {
                return createdAt == null ? null : OffsetDateTime.parse(createdAt);
            }
        };
    }

    private PlayerLastPlayedAtProjection lastPlayedAt(Long playerId, String playedAt) {
        return new SpelAwareProxyProjectionFactory().createProjection(
            PlayerLastPlayedAtProjection.class,
            Map.of(
                "playerId", playerId,
                "lastPlayedAt", Instant.parse(playedAt)
            )
        );
    }
}
