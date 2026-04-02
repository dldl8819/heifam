package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupPlayerUpdateRequest;
import com.balancify.backend.api.group.dto.GroupPlayerMmrUpdateRequest;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.PlayerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
            new GroupPlayerUpdateRequest(" 새닉네임 ", "tz")
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
            new GroupPlayerUpdateRequest(null, "R")
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
                new GroupPlayerUpdateRequest("   ", " ")
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
                new GroupPlayerUpdateRequest("새닉네임", null)
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
                new GroupPlayerUpdateRequest(null, "X")
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Race must be one of P,T,Z,PT,PZ,TZ,R");
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
