package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupPlayerImportRequest;
import com.balancify.backend.api.group.dto.GroupPlayerImportResponse;
import com.balancify.backend.api.group.dto.GroupPlayerImportRowRequest;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerImportServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private GroupRepository groupRepository;

    private PlayerImportService playerImportService;

    @BeforeEach
    void setUp() {
        playerImportService = new PlayerImportService(playerRepository, groupRepository);
    }

    @Test
    void importsPlayersWithCreateUpdateAndValidationRules() {
        Group group = new Group();
        group.setId(1L);
        group.setName("Group 1");

        Player existing = new Player();
        existing.setId(99L);
        existing.setGroup(group);
        existing.setNickname("Bravo");
        existing.setTier("B");
        existing.setBaseMmr(500);
        existing.setMmr(500);

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndNicknameIgnoreCase(1L, "Alpha")).thenReturn(List.of());
        when(playerRepository.findByGroup_IdAndNicknameIgnoreCase(1L, "Bravo")).thenReturn(List.of(existing));
        when(playerRepository.findByGroup_IdAndNicknameIgnoreCase(1L, "Delta")).thenReturn(List.of());
        when(playerRepository.findByGroup_IdAndNicknameIgnoreCase(1L, "Echo")).thenReturn(List.of());
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GroupPlayerImportRequest request = new GroupPlayerImportRequest(List.of(
            new GroupPlayerImportRowRequest("  Alpha  ", "A", 800, null, ""),
            new GroupPlayerImportRowRequest("Bravo", "A+", 900, 950, null),
            new GroupPlayerImportRowRequest("", "A", 800, 800, ""),
            new GroupPlayerImportRowRequest("Charlie", "", 700, 700, ""),
            new GroupPlayerImportRowRequest("Delta", "B", null, null, ""),
            new GroupPlayerImportRowRequest("Echo", "재배정대상", null, null, "재배정 대상"),
            new GroupPlayerImportRowRequest("alpha", "A", 800, 810, "")
        ));

        GroupPlayerImportResponse response = playerImportService.importPlayers(1L, request);

        assertThat(response.totalRows()).isEqualTo(7);
        assertThat(response.createdCount()).isEqualTo(3);
        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(3);
        assertThat(response.failedRows())
            .extracting(failedRow -> failedRow.reason())
            .containsExactlyInAnyOrder(
                "Nickname is required",
                "Tier is required",
                "Duplicate nickname in payload"
            );

        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository, times(4)).save(playerCaptor.capture());

        Player alpha = playerCaptor.getAllValues()
            .stream()
            .filter(player -> "Alpha".equals(player.getNickname()))
            .findFirst()
            .orElseThrow();
        assertThat(alpha.getGroup()).isSameAs(group);
        assertThat(alpha.getTier()).isEqualTo("A");
        assertThat(alpha.getBaseMmr()).isEqualTo(800);
        assertThat(alpha.getMmr()).isEqualTo(800);
        assertThat(alpha.getNote()).isNull();

        Player bravo = playerCaptor.getAllValues()
            .stream()
            .filter(player -> "Bravo".equals(player.getNickname()))
            .findFirst()
            .orElseThrow();
        assertThat(bravo.getId()).isEqualTo(99L);
        assertThat(bravo.getTier()).isEqualTo("A+");
        assertThat(bravo.getBaseMmr()).isEqualTo(900);
        assertThat(bravo.getMmr()).isEqualTo(950);

        Player delta = playerCaptor.getAllValues()
            .stream()
            .filter(player -> "Delta".equals(player.getNickname()))
            .findFirst()
            .orElseThrow();
        assertThat(delta.getTier()).isEqualTo("B");
        assertThat(delta.getBaseMmr()).isEqualTo(500);
        assertThat(delta.getMmr()).isEqualTo(500);

        Player echo = playerCaptor.getAllValues()
            .stream()
            .filter(player -> "Echo".equals(player.getNickname()))
            .findFirst()
            .orElseThrow();
        assertThat(echo.getTier()).isEqualTo("NONE");
        assertThat(echo.getBaseMmr()).isZero();
        assertThat(echo.getMmr()).isZero();
        assertThat(echo.getNote()).isEqualTo("재배정 대상");
    }

    @Test
    void createsGroupWhenTargetGroupDoesNotExist() {
        Group createdGroup = new Group();
        createdGroup.setId(5L);
        createdGroup.setName("Group 5");

        when(groupRepository.findById(5L)).thenReturn(Optional.empty());
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
            Group group = invocation.getArgument(0);
            createdGroup.setId(group.getId());
            createdGroup.setName(group.getName());
            return createdGroup;
        });
        when(playerRepository.findByGroup_IdAndNicknameIgnoreCase(5L, "Newbie")).thenReturn(List.of());
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GroupPlayerImportRequest request = new GroupPlayerImportRequest(List.of(
            new GroupPlayerImportRowRequest("Newbie", "B", null, null, "")
        ));

        GroupPlayerImportResponse response = playerImportService.importPlayers(5L, request);

        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.updatedCount()).isZero();
        assertThat(response.failedCount()).isZero();

        ArgumentCaptor<Group> groupCaptor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getId()).isEqualTo(5L);
        assertThat(groupCaptor.getValue().getName()).isEqualTo("Group 5");
    }

    @Test
    void appliesTierDefaultMmrWhenOnlyNicknameAndTierProvided() {
        Group group = new Group();
        group.setId(7L);
        group.setName("Group 7");

        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndNicknameIgnoreCase(7L, "뉴비")).thenReturn(List.of());
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GroupPlayerImportRequest request = new GroupPlayerImportRequest(List.of(
            new GroupPlayerImportRowRequest("뉴비", "S", null, null, "")
        ));

        GroupPlayerImportResponse response = playerImportService.importPlayers(7L, request);

        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();

        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(playerCaptor.capture());
        assertThat(playerCaptor.getValue().getTier()).isEqualTo("S");
        assertThat(playerCaptor.getValue().getBaseMmr()).isEqualTo(1000);
        assertThat(playerCaptor.getValue().getMmr()).isEqualTo(1000);
    }

    @Test
    void importsPlayerRaceWhenProvided() {
        Group group = new Group();
        group.setId(8L);
        group.setName("Group 8");

        when(groupRepository.findById(8L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndNicknameIgnoreCase(8L, "민식")).thenReturn(List.of());
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GroupPlayerImportRequest request = new GroupPlayerImportRequest(List.of(
            new GroupPlayerImportRowRequest("민식", "B+", null, null, "", "PT")
        ));

        GroupPlayerImportResponse response = playerImportService.importPlayers(8L, request);

        assertThat(response.failedCount()).isZero();
        assertThat(response.createdCount()).isEqualTo(1);

        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(playerCaptor.capture());
        assertThat(playerCaptor.getValue().getRace()).isEqualTo("PT");
        assertThat(playerCaptor.getValue().getBaseMmr()).isEqualTo(600);
        assertThat(playerCaptor.getValue().getMmr()).isEqualTo(600);
    }
}
