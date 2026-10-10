package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.PrizeDrawListResponse;
import com.balancify.backend.api.group.dto.PrizeDrawResponse;
import com.balancify.backend.api.group.dto.PrizeDrawSaveRequest;
import com.balancify.backend.api.group.dto.PrizeDrawWinnerRequest;
import com.balancify.backend.api.group.dto.PrizeDrawWinnerResponse;
import com.balancify.backend.repository.PrizeDrawRepository;
import com.balancify.backend.repository.PrizeDrawRepository.DrawRow;
import com.balancify.backend.repository.PrizeDrawRepository.WinnerRow;
import com.balancify.backend.service.exception.BoardForbiddenException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrizeDrawServiceTest {

    private static final String SUPER = "owner@hei.gg";
    private static final String ADMIN = "ops@hei.gg";
    private static final String MEMBER = "member@hei.gg";
    private static final OffsetDateTime DRAWN = OffsetDateTime.parse("2026-10-10T21:00:00+09:00");

    @Mock
    private PrizeDrawRepository prizeDrawRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    private PrizeDrawService prizeDrawService;

    @BeforeEach
    void setUp() {
        prizeDrawService = new PrizeDrawService(prizeDrawRepository, accessControlService, operationAuditLogService);
        when(accessControlService.isAdminEmail(ADMIN)).thenReturn(true);
        when(accessControlService.isAdminEmail(SUPER)).thenReturn(true);
        when(accessControlService.isSuperAdminEmail(SUPER)).thenReturn(true);
        when(accessControlService.resolveDisplayNicknames(anyCollection())).thenReturn(Map.of(ADMIN, "OpsUser"));
        when(prizeDrawRepository.insertDraw(any(), any(), any(), anyInt(), any())).thenReturn(40L);
    }

    private static PrizeDrawWinnerRequest winner(Integer place, String name, Long playerId, String prize) {
        return new PrizeDrawWinnerRequest(place, name, playerId, prize);
    }

    private static PrizeDrawSaveRequest draw(String title, String mode, Integer entrants, PrizeDrawWinnerRequest... winners) {
        return new PrizeDrawSaveRequest(title, mode, entrants, Arrays.asList(winners));
    }

    @Test
    void savesADrawWithItsWinnersInOrderOfPlace() {
        when(prizeDrawRepository.countGroupPlayers(1L, Set.of(7L))).thenReturn(1L);

        prizeDrawService.save(
            1L,
            " Ops@Hei.gg ",
            "OpsUser",
            draw("  October draw  ", " last ", 30, winner(2, "  Typed Name ", null, "  "), winner(1, "YOUR_USERNAME", 7L, " mouse "))
        );

        verify(prizeDrawRepository).insertDraw(1L, "October draw", "LAST", 30, ADMIN);
        InOrder order = inOrder(prizeDrawRepository);
        order.verify(prizeDrawRepository).insertWinner(40L, 1, "YOUR_USERNAME", 7L, "mouse");
        order.verify(prizeDrawRepository).insertWinner(40L, 2, "Typed Name", null, null);
        verify(operationAuditLogService).recordPrizeDraw(
            OperationAuditLogService.ACTION_PRIZE_DRAW_SAVED, ADMIN, "OpsUser", 40L, 1L, "October draw",
            "entrants=30, winners=2, mode=LAST"
        );
    }

    @Test
    void letsOnlyAdminsSaveADraw() {
        assertThatThrownBy(() -> prizeDrawService.save(1L, MEMBER, "YOUR_USERNAME", draw("t", "FIRST", 5, winner(1, "a", null, null))))
            .isInstanceOf(BoardForbiddenException.class);

        verify(prizeDrawRepository, never()).insertDraw(any(), any(), any(), anyInt(), any());
    }

    @Test
    void refusesADrawThatCouldNotHaveBeenRun() {
        List<PrizeDrawWinnerRequest> eleven = new ArrayList<>();
        for (int place = 1; place <= PrizeDrawService.MAX_WINNERS + 1; place++) {
            eleven.add(winner(place, "name " + place, null, null));
        }
        List<PrizeDrawSaveRequest> refused = List.of(
            draw(" ", "FIRST", 5, winner(1, "a", null, null)),
            draw("x".repeat(PrizeDrawService.MAX_TITLE_LENGTH + 1), "FIRST", 5, winner(1, "a", null, null)),
            draw("t", "MIDDLE", 5, winner(1, "a", null, null)),
            draw("t", null, 5, winner(1, "a", null, null)),
            draw("t", "FIRST", 1, winner(1, "a", null, null)),
            draw("t", "FIRST", null, winner(1, "a", null, null)),
            draw("t", "FIRST", PrizeDrawService.MAX_ENTRANTS + 1, winner(1, "a", null, null)),
            // No winner, more winners than balls, more than a draw has places.
            draw("t", "FIRST", 5),
            draw("t", "FIRST", 2, winner(1, "a", null, null), winner(2, "b", null, null), winner(3, "c", null, null)),
            new PrizeDrawSaveRequest("t", "FIRST", 50, eleven),
            // Places that are missing, twice, or not places.
            draw("t", "FIRST", 5, winner(2, "a", null, null)),
            draw("t", "FIRST", 5, winner(1, "a", null, null), winner(1, "b", null, null)),
            draw("t", "FIRST", 5, winner(1, "a", null, null), winner(3, "b", null, null)),
            draw("t", "FIRST", 5, winner(0, "a", null, null)),
            draw("t", "FIRST", 5, winner(null, "a", null, null)),
            // Names and prizes out of bounds, and one player twice.
            draw("t", "FIRST", 5, winner(1, "  ", null, null)),
            draw("t", "FIRST", 5, winner(1, "x".repeat(PrizeDrawService.MAX_NAME_LENGTH + 1), null, null)),
            draw("t", "FIRST", 5, winner(1, "a", null, "x".repeat(PrizeDrawService.MAX_PRIZE_LENGTH + 1))),
            draw("t", "FIRST", 5, winner(1, "a", 7L, null), winner(2, "b", 7L, null))
        );

        for (PrizeDrawSaveRequest request : refused) {
            assertThatThrownBy(() -> prizeDrawService.save(1L, ADMIN, "OpsUser", request))
                .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> prizeDrawService.save(1L, ADMIN, "OpsUser", null)).isInstanceOf(IllegalArgumentException.class);
        verify(prizeDrawRepository, never()).insertDraw(any(), any(), any(), anyInt(), any());
        verify(prizeDrawRepository, never()).insertWinner(any(), anyInt(), any(), any(), any());
    }

    @Test
    void refusesAWinnerSaidToBeAPlayerWhoIsNotOnTheRoster() {
        when(prizeDrawRepository.countGroupPlayers(1L, Set.of(7L, 8L))).thenReturn(1L);

        assertThatThrownBy(() -> prizeDrawService.save(
            1L, ADMIN, "OpsUser", draw("t", "FIRST", 5, winner(1, "a", 7L, null), winner(2, "b", 8L, null))
        )).isInstanceOf(IllegalArgumentException.class);

        verify(prizeDrawRepository, never()).insertDraw(any(), any(), any(), anyInt(), any());
    }

    @Test
    void takesTheLargestDrawItAllows() {
        PrizeDrawWinnerRequest[] ten = new PrizeDrawWinnerRequest[PrizeDrawService.MAX_WINNERS];
        for (int index = 0; index < ten.length; index++) {
            ten[index] = winner(index + 1, "x".repeat(PrizeDrawService.MAX_NAME_LENGTH), null, "y".repeat(PrizeDrawService.MAX_PRIZE_LENGTH));
        }

        prizeDrawService.save(1L, ADMIN, "OpsUser", draw("x".repeat(PrizeDrawService.MAX_TITLE_LENGTH), "first", PrizeDrawService.MAX_ENTRANTS, ten));

        verify(prizeDrawRepository).insertDraw(eq(1L), any(), eq("FIRST"), eq(PrizeDrawService.MAX_ENTRANTS), eq(ADMIN));
    }

    @Test
    void listsTheDrawsWithTheirWinnersForEveryMember() {
        when(prizeDrawRepository.listDraws(1L, PrizeDrawService.LIST_LIMIT)).thenReturn(List.of(
            new DrawRow(41L, "second", "LAST", 12, " Ops@Hei.gg ", DRAWN),
            new DrawRow(40L, "first", "FIRST", 30, null, DRAWN.minusDays(7))
        ));
        when(prizeDrawRepository.listWinners(List.of(41L, 40L))).thenReturn(List.of(
            new WinnerRow(40L, 1, "YOUR_USERNAME", "mouse"),
            new WinnerRow(40L, 2, "Typed Name", null),
            new WinnerRow(41L, 1, "OtherUser", null)
        ));

        PrizeDrawListResponse forMember = prizeDrawService.list(1L, MEMBER);

        assertThat(forMember.canRun()).isFalse();
        assertThat(forMember.draws())
            .extracting(
                PrizeDrawResponse::id, PrizeDrawResponse::title, PrizeDrawResponse::mode, PrizeDrawResponse::entrantCount,
                PrizeDrawResponse::savedByNickname, PrizeDrawResponse::canDelete
            )
            .containsExactly(
                tuple(41L, "second", "LAST", 12, "OpsUser", false),
                tuple(40L, "first", "FIRST", 30, null, false)
            );
        assertThat(forMember.draws().get(1).winners()).containsExactly(
            new PrizeDrawWinnerResponse(1, "YOUR_USERNAME", "mouse"), new PrizeDrawWinnerResponse(2, "Typed Name", null)
        );
        assertThat(forMember.draws().get(0).winners()).containsExactly(new PrizeDrawWinnerResponse(1, "OtherUser", null));
    }

    @Test
    void tellsAdminsTheyMayRunADrawAndSuperAdminsTheyMayRemoveOne() {
        when(prizeDrawRepository.listDraws(1L, PrizeDrawService.LIST_LIMIT))
            .thenReturn(List.of(new DrawRow(40L, "first", "FIRST", 30, ADMIN, DRAWN)));

        PrizeDrawListResponse forAdmin = prizeDrawService.list(1L, ADMIN);
        PrizeDrawListResponse forSuper = prizeDrawService.list(1L, SUPER);

        assertThat(forAdmin.canRun()).isTrue();
        assertThat(forAdmin.draws()).extracting(PrizeDrawResponse::canDelete).containsExactly(false);
        assertThat(forSuper.canRun()).isTrue();
        assertThat(forSuper.draws()).extracting(PrizeDrawResponse::canDelete).containsExactly(true);
    }

    @Test
    void letsOnlySuperAdminsRemoveARecordAndLogsIt() {
        when(prizeDrawRepository.findDraw(1L, 40L)).thenReturn(Optional.of(new DrawRow(40L, "first", "FIRST", 30, ADMIN, DRAWN)));

        for (String actor : new String[] {MEMBER, ADMIN}) {
            assertThatThrownBy(() -> prizeDrawService.delete(1L, 40L, actor, "name")).isInstanceOf(BoardForbiddenException.class);
        }
        assertThatThrownBy(() -> prizeDrawService.delete(1L, 99L, SUPER, "Owner")).isInstanceOf(NoSuchElementException.class);
        verify(prizeDrawRepository, never()).deleteDraw(any());

        prizeDrawService.delete(1L, 40L, " Owner@Hei.gg ", "Owner");

        verify(prizeDrawRepository).deleteDraw(40L);
        verify(operationAuditLogService).recordPrizeDraw(
            OperationAuditLogService.ACTION_PRIZE_DRAW_DELETED, SUPER, "Owner", 40L, 1L, "first", null
        );
    }
}
