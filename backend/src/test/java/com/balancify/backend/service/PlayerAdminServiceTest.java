package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupPlayerUpdateRequest;
import com.balancify.backend.api.group.dto.GroupPlayerMmrUpdateRequest;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.PlayerRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PlayerAdminServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    private PlayerAdminService playerAdminService;

    @BeforeEach
    void setUp() {
        playerAdminService = new PlayerAdminService(playerRepository);
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
    void storesChatLeaveMetadataWhenDeactivating() {
        Player player = player(10L, 1L, "기존닉");
        OffsetDateTime chatLeftAt = OffsetDateTime.parse("2026-05-02T12:41:00+09:00");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, null, false, chatLeftAt, " 개인 사정 ", null, null)
        );

        assertThat(player.isActive()).isFalse();
        assertThat(player.getChatLeftAt()).isEqualTo(chatLeftAt);
        assertThat(player.getChatLeftReason()).isEqualTo("개인 사정");
        assertThat(player.getChatRejoinedAt()).isNull();
        verify(playerRepository, never()).findByGroup_IdAndNicknameIgnoreCase(anyLong(), anyString());
        verify(playerRepository).save(player);
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
    void throwsWhenChatLeftReasonIsTooLong() {
        Player player = player(10L, 1L, "기존닉");
        OffsetDateTime chatLeftAt = OffsetDateTime.parse("2026-05-02T12:41:00+09:00");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() ->
            playerAdminService.updatePlayer(
                1L,
                10L,
                new GroupPlayerUpdateRequest(null, null, false, chatLeftAt, "a".repeat(501), null, null)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Chat left reason must be 500 characters or fewer");

        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void throwsWhenReactivatingWithoutChatRejoinedAt() {
        Player player = player(10L, 1L, "기존닉");
        player.setActive(false);
        player.setChatLeftAt(OffsetDateTime.parse("2026-05-02T12:41:00+09:00"));
        player.setChatLeftReason("개인 사정");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() ->
            playerAdminService.updatePlayer(
                1L,
                10L,
                new GroupPlayerUpdateRequest(null, null, true, null, null, null, null)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Chat rejoined time is required when reactivating player");

        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void storesChatRejoinedAtWhenReactivatingWithoutClearingLeaveMetadata() {
        Player player = player(10L, 1L, "기존닉");
        OffsetDateTime chatLeftAt = OffsetDateTime.parse("2026-05-02T12:41:00+09:00");
        OffsetDateTime chatRejoinedAt = OffsetDateTime.parse("2026-05-03T13:42:00+09:00");
        player.setActive(false);
        player.setChatLeftAt(chatLeftAt);
        player.setChatLeftReason("개인 사정");
        when(playerRepository.findByIdAndGroup_Id(10L, 1L)).thenReturn(Optional.of(player));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        playerAdminService.updatePlayer(
            1L,
            10L,
            new GroupPlayerUpdateRequest(null, null, true, null, null, chatRejoinedAt, null)
        );

        assertThat(player.isActive()).isTrue();
        assertThat(player.getChatLeftAt()).isEqualTo(chatLeftAt);
        assertThat(player.getChatLeftReason()).isEqualTo("개인 사정");
        assertThat(player.getChatRejoinedAt()).isEqualTo(chatRejoinedAt);
        verify(playerRepository).save(player);
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
