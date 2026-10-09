package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.NicknameRequestListResponse;
import com.balancify.backend.api.group.dto.NicknameRequestResponse;
import com.balancify.backend.repository.NicknameRequestRepository;
import com.balancify.backend.repository.NicknameRequestRepository.RequestRow;
import com.balancify.backend.service.exception.BoardLimitException;
import com.balancify.backend.service.exception.NicknameRequestConflictException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NicknameRequestServiceTest {

    private static final String ADMIN = "ops@hei.gg";
    private static final String MEMBER = "member@hei.gg";
    private static final String OTHER = "other@hei.gg";
    private static final OffsetDateTime ASKED = OffsetDateTime.parse("2026-10-10T10:00:00+09:00");
    private static final Map<String, String> NICKNAMES = Map.of(ADMIN, "OpsUser", MEMBER, "YOUR_USERNAME", OTHER, "OtherUser");

    @Mock
    private NicknameRequestRepository nicknameRequestRepository;

    @Mock
    private AccessControlService accessControlService;

    private NicknameRequestService nicknameRequestService;

    @BeforeEach
    void setUp() {
        nicknameRequestService = new NicknameRequestService(nicknameRequestRepository, accessControlService);
        when(accessControlService.isAdminEmail(ADMIN)).thenReturn(true);
        when(accessControlService.resolveDisplayNickname(any())).thenAnswer(call -> NICKNAMES.get(call.<String>getArgument(0)));
        when(accessControlService.resolveDisplayNicknames(anyCollection())).thenAnswer(call -> {
            Map<String, String> found = new HashMap<>();
            call.<Collection<String>>getArgument(0).forEach(email -> found.put(email, NICKNAMES.get(email)));
            return found;
        });
        when(nicknameRequestRepository.cancel(anyLong())).thenReturn(true);
        when(nicknameRequestRepository.decide(anyLong(), any(), any(), any())).thenReturn(true);
    }

    private static RequestRow row(long id, String requester, String status, String decidedBy) {
        return new RequestRow(
            id, requester, NICKNAMES.get(requester), "NEW_NAME_" + id, "reason " + id, status,
            decidedBy == null ? null : "note " + id, decidedBy, decidedBy == null ? null : ASKED.plusHours(1), ASKED
        );
    }

    @Test
    void showsAMemberOnlyTheirOwnRequests() {
        when(nicknameRequestRepository.listByRequester(1L, MEMBER, NicknameRequestService.OWN_LIST_LIMIT))
            .thenReturn(List.of(row(3, MEMBER, "PENDING", null), row(2, MEMBER, "REJECTED", ADMIN)));

        NicknameRequestListResponse response = nicknameRequestService.list(1L, " Member@Hei.gg ");

        assertThat(response.admin()).isFalse();
        assertThat(response.currentNickname()).isEqualTo("YOUR_USERNAME");
        assertThat(response.received()).isEmpty();
        assertThat(response.mine())
            .extracting(
                NicknameRequestResponse::id, NicknameRequestResponse::status, NicknameRequestResponse::mine,
                NicknameRequestResponse::canCancel, NicknameRequestResponse::canDecide,
                NicknameRequestResponse::processedByNickname
            )
            .containsExactly(
                tuple(3L, "PENDING", true, true, false, null),
                tuple(2L, "REJECTED", true, false, false, "OpsUser")
            );
        verify(nicknameRequestRepository, never()).listForAdmins(any(), anyInt());
    }

    @Test
    void showsAdminsEveryRequestAndLetsThemDecideThoseStillWaiting() {
        when(nicknameRequestRepository.listByRequester(1L, ADMIN, NicknameRequestService.OWN_LIST_LIMIT)).thenReturn(List.of());
        when(nicknameRequestRepository.listForAdmins(1L, NicknameRequestService.ADMIN_LIST_LIMIT))
            .thenReturn(List.of(row(5, MEMBER, "PENDING", null), row(4, OTHER, "APPROVED", ADMIN)));

        NicknameRequestListResponse response = nicknameRequestService.list(1L, ADMIN);

        assertThat(response.admin()).isTrue();
        assertThat(response.mine()).isEmpty();
        assertThat(response.received())
            .extracting(
                NicknameRequestResponse::id, NicknameRequestResponse::currentNickname,
                NicknameRequestResponse::desiredNickname, NicknameRequestResponse::mine,
                NicknameRequestResponse::canCancel, NicknameRequestResponse::canDecide
            )
            .containsExactly(
                tuple(5L, "YOUR_USERNAME", "NEW_NAME_5", false, false, true),
                tuple(4L, "OtherUser", "NEW_NAME_4", false, false, false)
            );
    }

    @Test
    void filesARequestWithTheNicknameTheAccountShowsNow() {
        nicknameRequestService.create(1L, " Member@Hei.gg ", "  NEW_NAME  ", "  because  ");

        verify(nicknameRequestRepository).insert(1L, MEMBER, "YOUR_USERNAME", "NEW_NAME", "because");
    }

    @Test
    void filesARequestWithoutAReasonOrAKnownNickname() {
        nicknameRequestService.create(1L, "nameless@hei.gg", "NEW_NAME", "   ");

        verify(nicknameRequestRepository).insert(eq(1L), eq("nameless@hei.gg"), isNull(), eq("NEW_NAME"), isNull());
    }

    @Test
    void refusesANicknameThatIsEmptyTooLongOrTheOneTheyHave() {
        for (String desired : new String[] {null, "   ", "x".repeat(NicknameRequestService.MAX_NICKNAME_LENGTH + 1), " YOUR_USERNAME "}) {
            assertThatThrownBy(() -> nicknameRequestService.create(1L, MEMBER, desired, null))
                .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> nicknameRequestService.create(
            1L, MEMBER, "NEW_NAME", "x".repeat(NicknameRequestService.MAX_REASON_LENGTH + 1)
        )).isInstanceOf(IllegalArgumentException.class);

        nicknameRequestService.create(1L, MEMBER, "x".repeat(NicknameRequestService.MAX_NICKNAME_LENGTH), null);

        verify(nicknameRequestRepository).insert(any(), any(), any(), any(), any());
    }

    @Test
    void refusesASecondRequestWhileOneIsWaiting() {
        when(nicknameRequestRepository.insert(any(), any(), any(), any(), any()))
            .thenThrow(new DuplicateKeyException("uq_nickname_change_requests_pending"));

        assertThatThrownBy(() -> nicknameRequestService.create(1L, MEMBER, "NEW_NAME", null))
            .isInstanceOf(NicknameRequestConflictException.class);
    }

    @Test
    void refusesMoreRequestsInADayThanOnePersonMayFile() {
        when(nicknameRequestRepository.countSince(eq(1L), eq(MEMBER), any()))
            .thenReturn((long) NicknameRequestService.REQUESTS_PER_DAY);

        assertThatThrownBy(() -> nicknameRequestService.create(1L, MEMBER, "NEW_NAME", null))
            .isInstanceOf(BoardLimitException.class);

        verify(nicknameRequestRepository, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void letsOnlyWhoeverAskedCallOffAWaitingRequest() {
        when(nicknameRequestRepository.find(1L, 7L)).thenReturn(Optional.of(row(7, MEMBER, "PENDING", null)));

        nicknameRequestService.cancel(1L, 7L, MEMBER);
        verify(nicknameRequestRepository).cancel(7L);

        // Somebody else's request reads as missing, to an admin too: admins decide, they do not call off.
        for (String actor : new String[] {OTHER, ADMIN}) {
            assertThatThrownBy(() -> nicknameRequestService.cancel(1L, 7L, actor))
                .isInstanceOf(NoSuchElementException.class);
        }
        assertThatThrownBy(() -> nicknameRequestService.cancel(1L, 8L, MEMBER))
            .isInstanceOf(NoSuchElementException.class);
        verify(nicknameRequestRepository).cancel(anyLong());
    }

    @Test
    void doesNotCallOffARequestThatIsNoLongerWaiting() {
        when(nicknameRequestRepository.find(1L, 7L)).thenReturn(Optional.of(row(7, MEMBER, "APPROVED", ADMIN)));
        when(nicknameRequestRepository.cancel(7L)).thenReturn(false);

        assertThatThrownBy(() -> nicknameRequestService.cancel(1L, 7L, MEMBER))
            .isInstanceOf(NicknameRequestConflictException.class);
    }

    @Test
    void recordsAnAdminsDecisionAndChangesNothingElse() {
        when(nicknameRequestRepository.find(1L, 7L)).thenReturn(Optional.of(row(7, MEMBER, "PENDING", null)));

        nicknameRequestService.decide(1L, 7L, " Ops@Hei.gg ", " approved ", "  changed today  ");
        nicknameRequestService.decide(1L, 7L, ADMIN, "REJECTED", "  ");

        verify(nicknameRequestRepository).decide(7L, "APPROVED", "changed today", ADMIN);
        verify(nicknameRequestRepository).decide(7L, "REJECTED", null, ADMIN);
        // A ticket only: no nickname is written anywhere.
        verify(accessControlService, never()).updateAllowedUserEmailNickname(any(), any(), any());
    }

    @Test
    void refusesADecisionThatIsNotOneOrFromSomeoneWhoMayNotDecide() {
        when(nicknameRequestRepository.find(1L, 7L)).thenReturn(Optional.of(row(7, MEMBER, "PENDING", null)));

        for (String status : new String[] {null, "", "PENDING", "CANCELED", "DONE"}) {
            assertThatThrownBy(() -> nicknameRequestService.decide(1L, 7L, ADMIN, status, null))
                .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> nicknameRequestService.decide(
            1L, 7L, ADMIN, "APPROVED", "x".repeat(NicknameRequestService.MAX_NOTE_LENGTH + 1)
        )).isInstanceOf(IllegalArgumentException.class);
        // A member, the one who asked included, is told nothing is there.
        assertThatThrownBy(() -> nicknameRequestService.decide(1L, 7L, MEMBER, "APPROVED", null))
            .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> nicknameRequestService.decide(1L, 9L, ADMIN, "APPROVED", null))
            .isInstanceOf(NoSuchElementException.class);

        verify(nicknameRequestRepository, never()).decide(anyLong(), any(), any(), any());
    }

    @Test
    void decidesARequestOnlyOnceAndNeverOneThatWasCalledOff() {
        when(nicknameRequestRepository.find(1L, 7L)).thenReturn(Optional.of(row(7, MEMBER, "REJECTED", ADMIN)));
        when(nicknameRequestRepository.decide(anyLong(), any(), any(), any())).thenReturn(false);
        when(nicknameRequestRepository.find(1L, 8L)).thenReturn(Optional.of(row(8, MEMBER, "CANCELED", null)));

        assertThatThrownBy(() -> nicknameRequestService.decide(1L, 7L, ADMIN, "APPROVED", null))
            .isInstanceOf(NicknameRequestConflictException.class);
        assertThatThrownBy(() -> nicknameRequestService.decide(1L, 8L, ADMIN, "APPROVED", null))
            .isInstanceOf(NoSuchElementException.class);
    }
}
