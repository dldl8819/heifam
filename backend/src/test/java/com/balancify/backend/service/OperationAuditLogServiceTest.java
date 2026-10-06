package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.domain.OperationAuditLog;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.OperationAuditLogRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class OperationAuditLogServiceTest {

    @Mock
    private OperationAuditLogRepository operationAuditLogRepository;

    @Mock
    private AccessControlService accessControlService;

    private OperationAuditLogService operationAuditLogService;

    @BeforeEach
    void setUp() {
        operationAuditLogService = new OperationAuditLogService(operationAuditLogRepository, accessControlService);
        lenient().when(operationAuditLogRepository.save(any(OperationAuditLog.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void productionConstructorIsExplicitlyAutowired() {
        var autowiredConstructors = java.util.Arrays.stream(
                OperationAuditLogService.class.getDeclaredConstructors()
            )
            .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
            .toList();

        assertThat(autowiredConstructors)
            .singleElement()
            .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                OperationAuditLogRepository.class,
                AccessControlService.class
            ));
    }

    @Test
    void recordsPlayerRegistrationAuditLog() {
        Player player = new Player();
        player.setId(10L);
        player.setNickname("PlayerOne");
        player.setTier("B+");
        player.setRace("P");

        operationAuditLogService.recordPlayerRegistration(
            "OPS@EXAMPLE.COM",
            "운영진",
            1L,
            player,
            true,
            false
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        OperationAuditLog log = logCaptor.getValue();

        assertThat(log.getAction()).isEqualTo(OperationAuditLogService.ACTION_PLAYER_REGISTERED);
        assertThat(log.getActorEmail()).isEqualTo("ops@example.com");
        assertThat(log.getActorNickname()).isEqualTo("운영진");
        assertThat(log.getTargetType()).isEqualTo("PLAYER");
        assertThat(log.getTargetId()).isEqualTo(10L);
        assertThat(log.getTargetLabel()).isEqualTo("PlayerOne");
        assertThat(log.getGroupId()).isEqualTo(1L);
        assertThat(log.getSummary()).isEqualTo("선수 등록");
        assertThat(log.getDetails()).isEqualTo("tier=B+, race=P");
    }

    @Test
    void recordsMatchDeletionAuditLog() {
        OffsetDateTime playedAt = OffsetDateTime.parse("2026-05-23T12:00:00Z");

        operationAuditLogService.recordMatchDeletion(
            "ops@example.com",
            "운영진",
            new MatchResultService.DeletedMatchAuditSnapshot(99L, 1L, playedAt, true)
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        OperationAuditLog log = logCaptor.getValue();

        assertThat(log.getAction()).isEqualTo(OperationAuditLogService.ACTION_MATCH_DELETED);
        assertThat(log.getTargetType()).isEqualTo("MATCH");
        assertThat(log.getTargetId()).isEqualTo(99L);
        assertThat(log.getTargetLabel()).isEqualTo("#99");
        assertThat(log.getGroupId()).isEqualTo(1L);
        assertThat(log.getSummary()).isEqualTo("경기 삭제");
        assertThat(log.getDetails()).isEqualTo("matchId=99, deletedAt=" + log.getCreatedAt());
    }

    @Test
    void recordsPrizeEventSteps() {
        operationAuditLogService.recordPrizeEvent(
            OperationAuditLogService.ACTION_PRIZE_EVENT_CONFIRMED, "ops@example.com", "운영진", 5L, 1L, "10월 이벤트", "winners=2, total=15000"
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getTargetType()).isEqualTo("PRIZE_EVENT");
        assertThat(logCaptor.getValue().getTargetLabel()).isEqualTo("10월 이벤트");
        assertThat(logCaptor.getValue().getSummary()).isEqualTo("상품 이벤트 확정");
        assertThat(logCaptor.getValue().getDetails()).isEqualTo("winners=2, total=15000");
    }

    @Test
    void recordsAnEarlyPredictionClose() {
        operationAuditLogService.recordPredictionsClosed("ops@example.com", "운영진", 30L, 1L);

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo(OperationAuditLogService.ACTION_PREDICTIONS_CLOSED);
        assertThat(logCaptor.getValue().getTargetLabel()).isEqualTo("#30");
        assertThat(logCaptor.getValue().getSummary()).isEqualTo("승부 예측 마감");
    }

    @Test
    void recordsTournamentCreationAndCancellation() {
        operationAuditLogService.recordTournamentCreated("ops@example.com", "운영진", 9L, 1L, 4, 2);
        operationAuditLogService.recordTournamentCancelled("ops@example.com", "운영진", 9L, 1L);

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository, times(2)).save(logCaptor.capture());
        OperationAuditLog created = logCaptor.getAllValues().get(0);
        OperationAuditLog cancelled = logCaptor.getAllValues().get(1);

        assertThat(created.getAction()).isEqualTo(OperationAuditLogService.ACTION_TOURNAMENT_CREATED);
        assertThat(created.getTargetType()).isEqualTo("TOURNAMENT");
        assertThat(created.getTargetLabel()).isEqualTo("#9");
        assertThat(created.getGroupId()).isEqualTo(1L);
        assertThat(created.getDetails()).isEqualTo("teams=4, waiting=2");
        assertThat(cancelled.getAction()).isEqualTo(OperationAuditLogService.ACTION_TOURNAMENT_CANCELLED);
        assertThat(cancelled.getSummary()).isEqualTo("팀 토너먼트 취소");
    }

    @Test
    void recordsMatchResultUpdateAuditLog() {
        operationAuditLogService.recordMatchResultUpdate(
            "ops@example.com",
            "OpsUser",
            new MatchResultService.MatchResultUpdateAuditSnapshot(
                99L,
                1L,
                "HOME",
                "AWAY",
                "PPP",
                "PPT"
            )
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        OperationAuditLog log = logCaptor.getValue();

        assertThat(log.getAction()).isEqualTo(OperationAuditLogService.ACTION_MATCH_RESULT_UPDATED);
        assertThat(log.getTargetType()).isEqualTo("MATCH");
        assertThat(log.getTargetId()).isEqualTo(99L);
        assertThat(log.getTargetLabel()).isEqualTo("#99");
        assertThat(log.getGroupId()).isEqualTo(1L);
        assertThat(log.getSummary()).isEqualTo("경기 결과 수정");
        assertThat(log.getDetails()).isEqualTo("승리 팀: 홈 → 어웨이 / 종족 조합: PPP → PPT");
    }

    @Test
    void marksParticipantRaceChangesInMatchResultUpdateAuditLog() {
        operationAuditLogService.recordMatchResultUpdate(
            "ops@example.com",
            "OpsUser",
            new MatchResultService.MatchResultUpdateAuditSnapshot(
                99L,
                1L,
                "HOME",
                "HOME",
                "PPT",
                "PPT",
                List.of(
                    new MatchResultService.ParticipantRaceChange("HOME", "P", "T"),
                    new MatchResultService.ParticipantRaceChange("HOME", "T", "P"),
                    new MatchResultService.ParticipantRaceChange("AWAY", "p", "t"),
                    new MatchResultService.ParticipantRaceChange("away", "P", "T")
                )
            )
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());

        // The winner and the composition stayed, so the entry says only whose races moved, by team.
        assertThat(logCaptor.getValue().getSummary()).isEqualTo("경기 종족 수정");
        assertThat(logCaptor.getValue().getDetails())
            .isEqualTo("선수 종족 (PPT): 홈 P→T 1명, T→P 1명; 어웨이 P→T 2명");
    }

    @Test
    void saysTheRacesWereSetAgainWhenNothingElseChanged() {
        operationAuditLogService.recordMatchResultUpdate(
            "ops@example.com",
            "OpsUser",
            new MatchResultService.MatchResultUpdateAuditSnapshot(99L, 1L, "HOME", "HOME", "PPT", "PPT")
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());

        assertThat(logCaptor.getValue().getSummary()).isEqualTo("경기 종족 수정");
        assertThat(logCaptor.getValue().getDetails()).isEqualTo("선수 종족을 종족 조합에 맞게 다시 배정");
    }

    @Test
    void describesAWinnerFlipWithAChangedCompositionAndRacesInOneEntry() {
        operationAuditLogService.recordMatchResultUpdate(
            "ops@example.com",
            "OpsUser",
            new MatchResultService.MatchResultUpdateAuditSnapshot(
                99L,
                1L,
                "HOME",
                "AWAY",
                "PPP",
                "PPZ",
                List.of(new MatchResultService.ParticipantRaceChange("AWAY", "P", "Z"))
            )
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());

        assertThat(logCaptor.getValue().getSummary()).isEqualTo("경기 결과 수정");
        assertThat(logCaptor.getValue().getDetails())
            .isEqualTo("승리 팀: 홈 → 어웨이 / 종족 조합: PPP → PPZ / 선수 종족: 어웨이 P→Z 1명");
    }

    @Test
    void recordsPlayerProfileUpdateAuditLogWithChangedFields() {
        Player player = new Player();
        player.setId(10L);
        player.setNickname("PlayerAlpha");
        player.setRace("PTZ");

        operationAuditLogService.recordPlayerProfileUpdate(
            "ops@example.com",
            "OpsUser",
            1L,
            player,
            "PlayerAlpha",
            "PlayerAlpha",
            "P",
            "PTZ"
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        OperationAuditLog log = logCaptor.getValue();

        assertThat(log.getAction()).isEqualTo(OperationAuditLogService.ACTION_PLAYER_REGISTRATION_UPDATED);
        assertThat(log.getTargetType()).isEqualTo("PLAYER");
        assertThat(log.getTargetId()).isEqualTo(10L);
        assertThat(log.getTargetLabel()).isEqualTo("PlayerAlpha");
        assertThat(log.getSummary()).isEqualTo("종족 수정");
        assertThat(log.getDetails()).isEqualTo("race=P -> PTZ");
    }

    @Test
    void recordsPlayerNicknameUpdateAuditLogWhenOnlyNicknameChanges() {
        Player player = new Player();
        player.setId(10L);
        player.setNickname("NewNickname");
        player.setRace("P");

        operationAuditLogService.recordPlayerProfileUpdate(
            "ops@example.com",
            "OpsUser",
            1L,
            player,
            "OldNickname",
            "NewNickname",
            "P",
            "P"
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        OperationAuditLog log = logCaptor.getValue();

        assertThat(log.getAction()).isEqualTo(OperationAuditLogService.ACTION_PLAYER_NICKNAME_UPDATED);
        assertThat(log.getSummary()).isEqualTo("닉네임 수정");
        assertThat(log.getDetails()).isEqualTo("nickname=OldNickname -> NewNickname");
    }

    @Test
    void recordsPlayerProfileUpdateAuditLogWhenNicknameAndRaceBothChange() {
        Player player = new Player();
        player.setId(10L);
        player.setNickname("NewNickname");
        player.setRace("PTZ");

        operationAuditLogService.recordPlayerProfileUpdate(
            "ops@example.com",
            "OpsUser",
            1L,
            player,
            "OldNickname",
            "NewNickname",
            "P",
            "PTZ"
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        OperationAuditLog log = logCaptor.getValue();

        assertThat(log.getAction()).isEqualTo(OperationAuditLogService.ACTION_PLAYER_PROFILE_UPDATED);
        assertThat(log.getSummary()).isEqualTo("선수 정보 수정");
        assertThat(log.getDetails()).isEqualTo("nickname=OldNickname -> NewNickname, race=P -> PTZ");
    }

    @Test
    void skipsPlayerProfileUpdateAuditLogWhenNothingChanges() {
        Player player = new Player();
        player.setId(10L);
        player.setNickname("PlayerAlpha");
        player.setRace("P");

        operationAuditLogService.recordPlayerProfileUpdate(
            "ops@example.com",
            "OpsUser",
            1L,
            player,
            "PlayerAlpha",
            "PlayerAlpha",
            "P",
            "P"
        );

        verify(operationAuditLogRepository, never()).save(any());
    }

    @Test
    void recordsPlayerTierUpdateAuditLogWithPreviousAndNextTier() {
        Player player = new Player();
        player.setId(10L);
        player.setNickname("PlayerAlpha");

        operationAuditLogService.recordPlayerTierUpdate(
            "ops@example.com",
            "OpsUser",
            1L,
            player,
            "C",
            "B+",
            1000,
            1200
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        OperationAuditLog log = logCaptor.getValue();

        assertThat(log.getAction()).isEqualTo(OperationAuditLogService.ACTION_PLAYER_TIER_UPDATED);
        assertThat(log.getTargetType()).isEqualTo("PLAYER");
        assertThat(log.getTargetId()).isEqualTo(10L);
        assertThat(log.getTargetLabel()).isEqualTo("PlayerAlpha");
        assertThat(log.getSummary()).isEqualTo("티어 수정");
        assertThat(log.getDetails()).isEqualTo("tier=C -> B+, mmr=1000 -> 1200");
    }

    @Test
    void recordsPlayerDeactivationAuditLog() {
        Player player = new Player();
        player.setId(10L);
        player.setNickname("PlayerAlpha");

        operationAuditLogService.recordPlayerActivityUpdate(
            "ops@example.com",
            "OpsUser",
            1L,
            player,
            true,
            false
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        OperationAuditLog log = logCaptor.getValue();

        assertThat(log.getAction()).isEqualTo(OperationAuditLogService.ACTION_PLAYER_DEACTIVATED);
        assertThat(log.getTargetType()).isEqualTo("PLAYER");
        assertThat(log.getTargetId()).isNull();
        assertThat(log.getTargetLabel()).isEqualTo(PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL);
        assertThat(log.getGroupId()).isEqualTo(1L);
        assertThat(log.getSummary()).isEqualTo("비활성");
        assertThat(log.getDetails()).isNull();
    }

    @Test
    void recordsPlayerReactivationAuditLog() {
        Player player = new Player();
        player.setId(10L);
        player.setNickname("PlayerAlpha");

        operationAuditLogService.recordPlayerActivityUpdate(
            "ops@example.com",
            "OpsUser",
            1L,
            player,
            false,
            true
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        OperationAuditLog log = logCaptor.getValue();

        assertThat(log.getAction()).isEqualTo(OperationAuditLogService.ACTION_PLAYER_REACTIVATED);
        assertThat(log.getSummary()).isEqualTo("복구");
        assertThat(log.getDetails()).isEqualTo("status=비활성 -> 활성");
    }

    @Test
    void returnsPagedAuditLogs() {
        OperationAuditLog log = new OperationAuditLog();
        log.setAction(OperationAuditLogService.ACTION_PLAYER_TIER_UPDATED);
        log.setTargetType("PLAYER");
        log.setSummary("티어 수정");
        log.setCreatedAt(OffsetDateTime.parse("2026-05-23T12:00:00Z"));

        when(operationAuditLogRepository.findAllByCreatedAtGreaterThanEqualOrderByCreatedAtDescIdDesc(
            any(OffsetDateTime.class),
            any(Pageable.class)
        ))
            .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(1, 20), 21));

        var response = operationAuditLogService.getLogs(1, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(21);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isTrue();
    }

    @Test
    void fillsMissingActorNicknameFromAccessProfileWithoutExposingActorEmail() {
        OperationAuditLog log = new OperationAuditLog();
        log.setAction(OperationAuditLogService.ACTION_PLAYER_TIER_UPDATED);
        log.setActorEmail("operator-id");
        log.setTargetType("PLAYER");
        log.setSummary("?곗뼱 ?섏젙");
        log.setCreatedAt(OffsetDateTime.parse("2026-06-09T12:00:00+09:00"));

        when(operationAuditLogRepository.findAllByCreatedAtGreaterThanEqualOrderByCreatedAtDescIdDesc(
            any(OffsetDateTime.class),
            any(Pageable.class)
        ))
            .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));
        when(accessControlService.resolveAccessProfile(eq("operator-id")))
            .thenReturn(new AccessControlService.AccessProfile(
                "operator-id",
                "OpsUser",
                "SUPER_ADMIN",
                true,
                true,
                true,
                true,
                null
            ));

        var response = operationAuditLogService.getLogs(0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).actorNickname()).isEqualTo("OpsUser");
        assertThat(response.items().get(0).actorEmail()).isNull();
    }

    @Test
    void excludesAuditLogsOlderThanTheRetentionWindowFromQueries() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-07T03:00:00Z");
        OperationAuditLogService fixedClockService = new OperationAuditLogService(
            operationAuditLogRepository,
            accessControlService,
            Clock.fixed(Instant.parse("2026-08-07T03:00:00Z"), ZoneOffset.UTC)
        );
        when(operationAuditLogRepository
            .findAllByCreatedAtGreaterThanEqualOrderByCreatedAtDescIdDesc(
                eq(now.minusYears(1)),
                any(Pageable.class)
            ))
            .thenReturn(new PageImpl<>(List.of()));

        fixedClockService.getLogs(0, 20);

        verify(operationAuditLogRepository)
            .findAllByCreatedAtGreaterThanEqualOrderByCreatedAtDescIdDesc(
                eq(now.minusYears(1)),
                any(Pageable.class)
            );
    }

    @Test
    void returnsFilteredAuditLogsUsingSpecification() {
        OperationAuditLog log = new OperationAuditLog();
        log.setAction(OperationAuditLogService.ACTION_PLAYER_REGISTRATION_UPDATED);
        log.setTargetType("PLAYER");
        log.setSummary("종족 수정");
        log.setDetails("race=P -> PTZ");
        log.setCreatedAt(OffsetDateTime.parse("2026-06-09T12:00:00+09:00"));

        when(operationAuditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));

        var response = operationAuditLogService.getLogs(
            0,
            20,
            new OperationAuditLogService.OperationAuditLogFilter(
                LocalDate.parse("2026-06-09"),
                LocalDate.parse("2026-06-09"),
                "ops",
                OperationAuditLogService.ACTION_PLAYER_REGISTRATION_UPDATED,
                "race",
                "PlayerAlpha"
            )
        );

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        verify(operationAuditLogRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void stampsTheActorsRoleOnEveryLog() {
        when(accessControlService.resolveActorRole("editor@example.com"))
            .thenReturn(AccessControlService.ACTOR_ROLE_RESULT_EDITOR);

        operationAuditLogService.recordMatchResultUpdate(
            "editor@example.com",
            "Editor",
            new MatchResultService.MatchResultUpdateAuditSnapshot(99L, 1L, "HOME", "AWAY", "PPT", "PPT")
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getActorRole()).isEqualTo(AccessControlService.ACTOR_ROLE_RESULT_EDITOR);
    }

    @Test
    void limitsTheResultEditorViewToLogsWrittenByEditorsEvenWithoutFilters() {
        when(operationAuditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        operationAuditLogService.getResultEditorLogs(0, 20, OperationAuditLogService.OperationAuditLogFilter.empty());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Specification<OperationAuditLog>> specificationCaptor =
            ArgumentCaptor.forClass(Specification.class);
        verify(operationAuditLogRepository).findAll(specificationCaptor.capture(), any(Pageable.class));
        verify(operationAuditLogRepository, never())
            .findAllByCreatedAtGreaterThanEqualOrderByCreatedAtDescIdDesc(any(), any());

        @SuppressWarnings("unchecked")
        Root<OperationAuditLog> root = mock(Root.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path<Object> actorRole = mock(Path.class);
        // The spec also reads other columns; only the role column matters here.
        lenient().when(root.get("actorRole")).thenReturn(actorRole);
        specificationCaptor.getValue().toPredicate(root, mock(CriteriaQuery.class), criteriaBuilder);

        verify(criteriaBuilder).equal(actorRole, AccessControlService.ACTOR_ROLE_RESULT_EDITOR);
    }
}
