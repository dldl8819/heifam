package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupPlayerUpdateRequest;
import com.balancify.backend.api.group.dto.GroupPlayerMmrUpdateRequest;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerLifecycleStatus;
import com.balancify.backend.repository.PlayerRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlayerAdminServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    private PlayerAdminService playerAdminService;
    @Mock
    private AccountDeletionService accountDeletionService;

    @Mock
    private EntityManager entityManager;


    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
            .doNothing()
            .when(accountDeletionService)
            .deactivatePlayer(anyLong(), any(OffsetDateTime.class), anyString());
        playerAdminService = new PlayerAdminService(
            playerRepository,
            operationAuditLogService,
            new GroupReadCacheService(0),
            accountDeletionService
        );
        ReflectionTestUtils.setField(playerAdminService, "entityManager", entityManager);
    }

    @Test
    void productionConstructorIsExplicitlyAutowired() {
        var autowiredConstructors = java.util.Arrays.stream(
                PlayerAdminService.class.getDeclaredConstructors()
            )
            .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
            .toList();

        assertThat(autowiredConstructors)
            .singleElement()
            .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                PlayerRepository.class,
                OperationAuditLogService.class,
                GroupReadCacheService.class,
                AccountDeletionService.class
            ));
    }

    @Test
    void updatesPlayerWhenNicknameAndRaceAreValid() {
        Player player = player(10L, 1L, "기존닉");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.findByGroup_IdAndNicknameIgnoreCase(1L, "새닉네임"))
            .thenReturn(List.of());
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(" 새닉네임 ", "tz", null, null, null, null, null)
        );

        verify(playerRepository).findByGroup_IdAndNicknameIgnoreCase(1L, "새닉네임");
        verify(playerRepository).save(player);
    }

    @Test
    void updatesOnlyRaceWhenNicknameIsNotProvided() {
        Player player = player(10L, 1L, "기존닉");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, "PTZ", null, null, null, null, null)
        );

        verify(playerRepository, never()).findByGroup_IdAndNicknameIgnoreCase(anyLong(), anyString());
        verify(playerRepository).save(player);
    }

    @Test
    void updatesTierAndMmrWhenTierIsValid() {
        Player player = player(10L, 1L, "PlayerAlpha");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, null, "b+", null, null, null, null, null)
        );

        assertThat(player.getTier()).isEqualTo("B+");
        assertThat(player.getBaseMmr()).isEqualTo(1200);
        assertThat(player.getMmr()).isEqualTo(1200);
        verify(playerRepository, never()).findByGroup_IdAndNicknameIgnoreCase(anyLong(), anyString());
        verify(playerRepository).save(player);
    }

    @Test
    void updatesTierAndMmrWhenTierIsD() {
        Player player = player(10L, 1L, "PlayerAlpha");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, null, "d", null, null, null, null, null)
        );

        assertThat(player.getTier()).isEqualTo("D");
        assertThat(player.getBaseMmr()).isEqualTo(1);
        assertThat(player.getMmr()).isEqualTo(1);
        verify(playerRepository).save(player);
    }

    @Test
    void seedsMmrWhenAssigningTierToUnassignedPlayerWithoutMmr() {
        Player player = player(10L, 1L, "PlayerAlpha");
        player.setTier("UNASSIGNED");
        player.setBaseMmr(0);
        player.setMmr(0);
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, null, "b+", null, null, null, null, null)
        );

        assertThat(player.getTier()).isEqualTo("B+");
        assertThat(player.getBaseMmr()).isEqualTo(1200);
        assertThat(player.getMmr()).isEqualTo(1200);
        verify(playerRepository).save(player);
    }

    @Test
    void recordsTierChangeAuditLogWhenTierIsUpdated() {
        Player player = player(10L, 1L, "PlayerAlpha");
        player.setTier("C");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, null, "b+", null, null, null, null, null),
            "ops@example.com",
            "OpsUser"
        );

        verify(operationAuditLogService).recordPlayerTierUpdate(
            eq("ops@example.com"),
            eq("OpsUser"),
            eq(1L),
            eq(player),
            eq("C"),
            eq("B+"),
            eq(800),
            eq(1200)
        );
    }

    @Test
    void recordsProfileUpdateAndDoesNotRecordTierUpdateWhenOnlyRaceChanges() {
        Player player = player(10L, 1L, "PlayerAlpha");
        player.setTier("A");
        player.setRace("P");
        player.setBaseMmr(800);
        player.setMmr(900);
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, "PTZ", null, null, null, null, null),
            "ops@example.com",
            "OpsUser"
        );

        assertThat(player.getRace()).isEqualTo("PTZ");
        assertThat(player.getMmr()).isEqualTo(900);
        verify(operationAuditLogService).recordPlayerProfileUpdate(
            eq("ops@example.com"),
            eq("OpsUser"),
            eq(1L),
            eq(player),
            eq("PlayerAlpha"),
            eq("PlayerAlpha"),
            eq("P"),
            eq("PTZ")
        );
        verify(operationAuditLogService, never()).recordPlayerTierUpdate(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void doesNotResetMmrOrRecordTierUpdateWhenTierIsUnchanged() {
        Player player = player(10L, 1L, "PlayerAlpha");
        player.setTier("B+");
        player.setBaseMmr(1200);
        player.setMmr(1420);
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, null, "B+", null, null, null, null, null),
            "ops@example.com",
            "OpsUser"
        );

        assertThat(player.getTier()).isEqualTo("B+");
        assertThat(player.getBaseMmr()).isEqualTo(1200);
        assertThat(player.getMmr()).isEqualTo(1420);
        verify(operationAuditLogService, never()).recordPlayerProfileUpdate(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
        verify(operationAuditLogService, never()).recordPlayerTierUpdate(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void resetsMmrWhenTierIsUnassigned() {
        Player player = player(10L, 1L, "PlayerAlpha");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, null, "UNASSIGNED", null, null, null, null, null)
        );

        assertThat(player.getTier()).isEqualTo("UNASSIGNED");
        assertThat(player.getBaseMmr()).isEqualTo(0);
        assertThat(player.getMmr()).isEqualTo(0);
        verify(playerRepository).save(player);
    }

    @Test
    void throwsWhenNicknameAndRaceAreBothMissing() {
        Player player = player(10L, 1L, "기존닉");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() ->
            playerAdminService.updatePlayer(
                1L,
                10L,
                new GroupPlayerUpdateRequest("   ", " ", null, null, null, null, null)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("At least one field is required");

        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void throwsWhenNicknameAlreadyExistsInSameGroup() {
        Player player = player(10L, 1L, "기존닉");
        Player duplicate = player(11L, 1L, "새닉네임");

        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.findByGroup_IdAndNicknameIgnoreCase(1L, "새닉네임"))
            .thenReturn(List.of(duplicate));

        assertThatThrownBy(() ->
            playerAdminService.updatePlayer(
                1L,
                10L,
                new GroupPlayerUpdateRequest("새닉네임", null, null, null, null, null, null)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Nickname already exists in group");
    }

    @Test
    void throwsWhenRaceIsInvalid() {
        Player player = player(10L, 1L, "기존닉");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() ->
            playerAdminService.updatePlayer(
                1L,
                10L,
                new GroupPlayerUpdateRequest(null, "X", null, null, null, null, null)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Race must be one of P,T,Z,PT,PZ,TZ,PTZ");
    }

    @Test
    void throwsWhenTierIsInvalid() {
        Player player = player(10L, 1L, "PlayerAlpha");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() ->
            playerAdminService.updatePlayer(
                1L,
                10L,
                new GroupPlayerUpdateRequest(null, null, "diamond", null, null, null, null, null)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Tier is invalid");

        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void delegatesDeactivationToTheLockedIdentityLifecyclePath() {
        Player player = player(10L, 1L, "기존닉");
        OffsetDateTime chatLeftAt = OffsetDateTime.parse("2026-05-02T12:41:00+09:00");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(entityManager.contains(player)).thenReturn(true);
        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, null, false, chatLeftAt, " 운영 정책 ", null, null)
        );

        verify(accountDeletionService).deactivatePlayer(10L, chatLeftAt, "운영 정책");
        verify(entityManager).detach(player);
        verify(playerRepository, never()).findByGroup_IdAndNicknameIgnoreCase(anyLong(), anyString());
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void recordsActivityAuditLogWhenDeactivating() {
        Player player = player(10L, 1L, "PlayerAlpha");
        OffsetDateTime chatLeftAt = OffsetDateTime.parse("2026-05-02T12:41:00+09:00");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, null, false, chatLeftAt, "본인 요청", null, null),
            "ops@example.com",
            "OpsUser"
        );

        verify(operationAuditLogService).recordPlayerActivityUpdate(
            eq("ops@example.com"),
            eq("OpsUser"),
            eq(1L),
            eq(player),
            eq(true),
            eq(false)
        );
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void rejectsProfileChangesCombinedWithDeactivation() {
        Player player = player(10L, 1L, "기존닉");
        OffsetDateTime chatLeftAt = OffsetDateTime.parse("2026-05-02T12:41:00+09:00");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest("새닉네임", null, false, chatLeftAt, "운영 정책", null, null)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Deactivation request contains unsupported fields");

        verify(accountDeletionService, never()).deactivatePlayer(
            anyLong(), any(OffsetDateTime.class), anyString()
        );
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void throwsWhenDeactivatingWithoutChatLeftAt() {
        Player player = player(10L, 1L, "기존닉");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() ->
            playerAdminService.updatePlayer(
                1L,
                10L,
                new GroupPlayerUpdateRequest(null, null, false, null, "개인 사정", null, null)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Chat left time is required when deactivating player");

        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void throwsWhenDeactivatingWithoutChatLeftReason() {
        Player player = player(10L, 1L, "기존닉");
        OffsetDateTime chatLeftAt = OffsetDateTime.parse("2026-05-02T12:41:00+09:00");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() ->
            playerAdminService.updatePlayer(
                1L,
                10L,
                new GroupPlayerUpdateRequest(null, null, false, chatLeftAt, "   ", null, null)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Chat left reason is required when deactivating player");

        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void rejectsFreeTextInactiveReason() {
        Player player = player(10L, 1L, "기존닉");
        OffsetDateTime chatLeftAt = OffsetDateTime.parse("2026-05-02T12:41:00+09:00");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() ->
            playerAdminService.updatePlayer(
                1L,
                10L,
                new GroupPlayerUpdateRequest(null, null, false, chatLeftAt, "개인 사정", null, null)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Chat left reason must use an allowed category");

        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void reactivatesOperationallyInactivePlayer() {
        Player player = player(10L, 1L, "기존닉");
        player.setActive(false);
        player.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        player.setChatLeftAt(OffsetDateTime.parse("2026-05-02T12:41:00+09:00"));
        player.setChatLeftReason("운영 정책");
        player.setIdentityRetainedUntil(OffsetDateTime.parse("2027-05-02T12:41:00+09:00"));
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        OffsetDateTime rejoinedAt = OffsetDateTime.parse("2026-05-03T13:42:00+09:00");
        when(playerRepository.reactivateRetainedInactivePlayer(
            eq(1L),
            eq(10L),
            eq(rejoinedAt),
            any(OffsetDateTime.class),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull()
        )).thenReturn(1);

        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(
                null,
                null,
                true,
                null,
                null,
                rejoinedAt,
                null
            )
        );

        assertThat(player.isActive()).isTrue();
        assertThat(player.getLifecycleStatus()).isEqualTo(PlayerLifecycleStatus.ACTIVE);
        assertThat(player.getIdentityRetainedUntil()).isNull();
        assertThat(player.getChatLeftAt()).isNull();
        assertThat(player.getChatLeftReason()).isNull();
        verify(playerRepository).reactivateRetainedInactivePlayer(
            eq(1L),
            eq(10L),
            eq(rejoinedAt),
            any(OffsetDateTime.class),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull()
        );
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void rejectsReactivationWhenExpirySweepWinsTheConditionalUpdate() {
        Player player = player(10L, 1L, "INACTIVE_PLAYER");
        player.setActive(false);
        player.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        player.setChatLeftAt(OffsetDateTime.parse("2026-05-02T12:41:00+09:00"));
        player.setChatLeftReason("운영 정책");
        player.setIdentityRetainedUntil(OffsetDateTime.parse("2027-05-02T12:41:00+09:00"));
        OffsetDateTime rejoinedAt = OffsetDateTime.parse("2026-05-03T13:42:00+09:00");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.reactivateRetainedInactivePlayer(
            eq(1L),
            eq(10L),
            eq(rejoinedAt),
            any(OffsetDateTime.class),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull()
        )).thenReturn(0);

        assertThatThrownBy(() -> playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, null, true, null, null, rejoinedAt, null)
        ))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("Player not found");

        assertThat(player.isActive()).isFalse();
        verify(playerRepository, never()).save(any(Player.class));
        verify(operationAuditLogService, never()).recordPlayerActivityUpdate(
            any(), any(), any(), any(), anyBoolean(), anyBoolean()
        );
    }

    @Test
    void rejectsReactivationWhenAnotherActivePlayerUsesTheRetainedNickname() {
        Player inactive = player(10L, 1L, "DUPLICATE_NICKNAME");
        inactive.setActive(false);
        inactive.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        inactive.setChatLeftAt(OffsetDateTime.parse("2026-05-02T12:41:00+09:00"));
        inactive.setChatLeftReason("운영 정책");
        inactive.setIdentityRetainedUntil(OffsetDateTime.parse("2027-05-02T12:41:00+09:00"));
        Player active = player(11L, 1L, "DUPLICATE_NICKNAME");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(inactive));
        when(playerRepository.findByGroup_IdAndNicknameIgnoreCase(1L, "DUPLICATE_NICKNAME"))
            .thenReturn(List.of(inactive, active));
        OffsetDateTime rejoinedAt = OffsetDateTime.parse("2026-05-03T13:42:00+09:00");

        assertThatThrownBy(() -> playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, null, true, null, null, rejoinedAt, null)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Nickname already exists in group");

        verify(playerRepository, never()).reactivateRetainedInactivePlayer(
            anyLong(),
            anyLong(),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class),
            org.mockito.ArgumentMatchers.nullable(String.class),
            org.mockito.ArgumentMatchers.nullable(UUID.class)
        );
    }

    @Test
    void restoresTheExactSharedAccountLinkWhenReactivating() {
        UUID authUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String retainedHash = AccountDeletionDataService.retentionSubjectHash(authUserId);
        Player player = player(12L, 1L, "공유계정선수");
        player.setActive(false);
        player.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        player.setChatLeftAt(OffsetDateTime.parse("2026-05-02T12:41:00+09:00"));
        player.setChatLeftReason("운영 정책");
        player.setIdentityRetainedUntil(OffsetDateTime.parse("2027-05-02T12:41:00+09:00"));
        player.setRetentionSubjectHash(retainedHash);
        OffsetDateTime rejoinedAt = OffsetDateTime.parse("2026-05-03T13:42:00+09:00");
        when(playerRepository.findByIdAndGroup_Id(12L, 1L)).thenReturn(Optional.of(player));
        when(accountDeletionService.resolveRetainedAuthUserId(retainedHash)).thenReturn(authUserId);
        when(playerRepository.reactivateRetainedInactivePlayer(
            eq(1L),
            eq(12L),
            eq(rejoinedAt),
            any(OffsetDateTime.class),
            eq(retainedHash),
            eq(authUserId)
        )).thenReturn(1);

        playerAdminService.updatePlayer(
            1L,
            12L,
            new GroupPlayerUpdateRequest(null, null, true, null, null, rejoinedAt, null)
        );

        verify(playerRepository).reactivateRetainedInactivePlayer(
            eq(1L),
            eq(12L),
            eq(rejoinedAt),
            any(OffsetDateTime.class),
            eq(retainedHash),
            eq(authUserId)
        );
    }

    @Test
    void refusesReactivationWhenTheRetainedAccountLinkCannotBeResolved() {
        Player player = player(13L, 1L, "연결확인선수");
        player.setActive(false);
        player.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        player.setChatLeftAt(OffsetDateTime.parse("2026-05-02T12:41:00+09:00"));
        player.setChatLeftReason("운영 정책");
        player.setIdentityRetainedUntil(OffsetDateTime.parse("2027-05-02T12:41:00+09:00"));
        player.setRetentionSubjectHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        OffsetDateTime rejoinedAt = OffsetDateTime.parse("2026-05-03T13:42:00+09:00");
        when(playerRepository.findByIdAndGroup_Id(13L, 1L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> playerAdminService.updatePlayer(
            1L,
            13L,
            new GroupPlayerUpdateRequest(null, null, true, null, null, rejoinedAt, null)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Retained account link could not be restored");

        verify(playerRepository, never()).reactivateRetainedInactivePlayer(
            anyLong(),
            anyLong(),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class),
            org.mockito.ArgumentMatchers.nullable(String.class),
            org.mockito.ArgumentMatchers.nullable(UUID.class)
        );
    }

    @Test
    void rejectsReactivationOfWithdrawnPlayer() {
        Player player = player(10L, 1L, "WITHDRAWN_PLAYER");
        player.setActive(false);
        player.setLifecycleStatus(PlayerLifecycleStatus.WITHDRAWN);
        player.setIdentityRetainedUntil(OffsetDateTime.parse("2031-05-02T12:41:00+09:00"));
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(
                null,
                null,
                true,
                null,
                null,
                OffsetDateTime.parse("2026-05-03T13:42:00+09:00"),
                null
            )
        ))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("Player not found");

        verify(playerRepository, never()).save(any(Player.class));
    }
    @Test
    void storesTierChangeAcknowledgementWithoutActiveStatusChange() {
        Player player = player(10L, 1L, "기존닉");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, null, null, null, null, null, "a+")
        );

        assertThat(player.getTierChangeAcknowledgedTier()).isEqualTo("A+");
        assertThat(player.getTierChangeAcknowledgedAt()).isNotNull();
        verify(playerRepository).save(player);
    }

    @Test
    void storesDormancyMmrFloorTierWithoutOtherPlayerChanges() {
        Player player = player(10L, 1L, "PlayerAlpha");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, null, null, null, null, null, null, null, "b+")
        );

        assertThat(player.getDormancyMmrFloorTier()).isEqualTo("B+");
        verify(playerRepository).save(player);
        verify(operationAuditLogService, never()).recordPlayerTierUpdate(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void clearsDormancyMmrFloorTierWhenDefaultPolicyIsRequested() {
        Player player = player(10L, 1L, "PlayerAlpha");
        player.setDormancyMmrFloorTier("B");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, null, null, null, null, null, null, null, "UNASSIGNED")
        );

        assertThat(player.getDormancyMmrFloorTier()).isNull();
        verify(playerRepository).save(player);
    }

    @Test
    void throwsWhenDormancyMmrFloorTierIsInvalid() {
        Player player = player(10L, 1L, "PlayerAlpha");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() ->
            playerAdminService.updatePlayer(
                1L,
                10L,
                new GroupPlayerUpdateRequest(null, null, null, null, null, null, null, null, "diamond")
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Dormancy MMR floor tier is invalid");

        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void throwsWhenTierChangeAcknowledgementTierIsInvalid() {
        Player player = player(10L, 1L, "기존닉");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() ->
            playerAdminService.updatePlayer(
                1L,
                10L,
                new GroupPlayerUpdateRequest(null, null, null, null, null, null, "diamond")
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Tier change acknowledgement tier is invalid");

        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void updatesPlayerMmrWhenValid() {
        Player player = player(10L, 1L, "기존닉");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        playerAdminService.updatePlayerMmr(1L, 10L, new GroupPlayerMmrUpdateRequest(1420));

        verify(playerRepository).save(player);
    }

    @Test
    void throwsWhenMmrIsMissing() {
        Player player = player(10L, 1L, "기존닉");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() ->
            playerAdminService.updatePlayerMmr(1L, 10L, new GroupPlayerMmrUpdateRequest(null))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("MMR is required");

        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void throwsWhenMmrIsOutOfRange() {
        Player player = player(10L, 1L, "기존닉");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() ->
            playerAdminService.updatePlayerMmr(1L, 10L, new GroupPlayerMmrUpdateRequest(-1))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("MMR must be between 0 and 5000");

        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void deletesPlayerWhenExists() {
        Player player = player(10L, 1L, "대상");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));

        playerAdminService.deletePlayer(1L, 10L);

        verify(playerRepository).delete(player);
        verify(playerRepository).flush();
    }

    @Test
    void throwsConflictFriendlyMessageWhenPlayerHasHistoryReferences() {
        Player player = player(10L, 1L, "대상");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        doThrow(new DataIntegrityViolationException("fk")).when(playerRepository).flush();

        assertThatThrownBy(() -> playerAdminService.deletePlayer(1L, 10L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("매치 또는 드래프트 기록이 남아 있는 선수는 삭제할 수 없습니다.");
    }

    private Player player(Long playerId, Long groupId, String nickname) {
        Group group = new Group();
        group.setId(groupId);
        group.setName("G");

        Player player = new Player();
        player.setId(playerId);
        player.setGroup(group);
        player.setNickname(nickname);
        player.setTier("A");
        player.setRace("P");
        player.setBaseMmr(800);
        player.setMmr(800);
        return player;
    }
}
