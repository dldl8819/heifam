package com.balancify.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.balancify.backend.config.PointProperties;
import com.balancify.backend.domain.CaptainDraft;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerLifecycleStatus;
import com.balancify.backend.repository.AccountPersonalDataRepository;
import com.balancify.backend.repository.AccountPersonalDataRepository.NicknameAccountCandidate;
import com.balancify.backend.security.SupabaseAuthAdminClient;
import com.balancify.backend.service.exception.AccountDeletionException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaptainDraftPointsTest {

    // Placeholders, as in every example of the repo.
    private static final String HOME_EMAIL = "your_username@example.com";
    private static final String AWAY_EMAIL = "your_other@example.com";
    private static final UUID AWAY_LOGIN = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private PointService pointService;

    @Mock
    private AccountPersonalDataRepository accountPersonalDataRepository;

    @Mock
    private SupabaseAuthAdminClient supabaseAuthAdminClient;

    private PointProperties pointProperties;
    private CaptainDraftPoints captainDraftPoints;

    @BeforeEach
    void setUp() {
        pointProperties = new PointProperties();
        captainDraftPoints = new CaptainDraftPoints(
            pointService,
            pointProperties,
            accountPersonalDataRepository,
            supabaseAuthAdminClient
        );
    }

    @Test
    void paysACaptainNamedLikeTheirAccountAndALinkedCaptainByTheirLogin() {
        CaptainDraft draft = draft(100L, captain(1L, "홈감독", null), captain(2L, "원정감독", AWAY_LOGIN));
        when(accountPersonalDataRepository.findAccountIdentitiesByNickname("홈감독"))
            .thenReturn(List.of(new NicknameAccountCandidate(HOME_EMAIL, null)));
        // public.users has no copy, so Supabase Auth is asked.
        when(accountPersonalDataRepository.findAccountEmail(AWAY_LOGIN)).thenReturn(Optional.empty());
        when(supabaseAuthAdminClient.findUserEmail(AWAY_LOGIN)).thenReturn(Optional.of(AWAY_EMAIL));

        captainDraftPoints.grantWhilePlayed(draft);

        verify(pointService).grantCaptainDraftPoint(HOME_EMAIL, 100L, 1L);
        verify(pointService).grantCaptainDraftPoint(AWAY_EMAIL, 100L, 2L);
    }

    @Test
    void looksNoAccountUpForACaptainAlreadyPaid() {
        CaptainDraft draft = draft(100L, captain(1L, "홈감독", null), captain(2L, "원정감독", AWAY_LOGIN));
        when(pointService.hasCaptainDraftPoint(100L, 1L)).thenReturn(true);
        when(pointService.hasCaptainDraftPoint(100L, 2L)).thenReturn(true);

        captainDraftPoints.grantWhilePlayed(draft);

        verify(pointService, never()).grantCaptainDraftPoint(anyString(), anyLong(), anyLong());
        verifyNoInteractions(accountPersonalDataRepository, supabaseAuthAdminClient);
    }

    @Test
    void paysNobodyWhenNoSingleAccountIsTheCaptainsOrAuthCannotBeRead() {
        CaptainDraft draft = draft(100L, captain(1L, "홈감독", null), captain(2L, "원정감독", AWAY_LOGIN));
        // Two accounts go by the home captain's name: neither is taken for theirs.
        when(accountPersonalDataRepository.findAccountIdentitiesByNickname("홈감독")).thenReturn(List.of(
            new NicknameAccountCandidate(HOME_EMAIL, null),
            new NicknameAccountCandidate(AWAY_EMAIL, null)
        ));
        when(accountPersonalDataRepository.findAccountEmail(AWAY_LOGIN)).thenReturn(Optional.empty());
        when(supabaseAuthAdminClient.findUserEmail(AWAY_LOGIN)).thenThrow(new AccountDeletionException(
            AccountDeletionException.Reason.AUTH_UNAVAILABLE, "unavailable"
        ));

        captainDraftPoints.grantWhilePlayed(draft);

        verify(pointService, never()).grantCaptainDraftPoint(anyString(), anyLong(), anyLong());
    }

    @Test
    void paysNoCaptainWhoLeftOrWhileTheAmountIsOff() {
        Player left = captain(1L, "홈감독", null);
        left.setLifecycleStatus(PlayerLifecycleStatus.ANONYMIZED);
        captainDraftPoints.grantWhilePlayed(draft(100L, left, null));

        pointProperties.setCaptainDraft(0);
        captainDraftPoints.grantWhilePlayed(draft(101L, captain(3L, "다른감독", null), null));

        verify(pointService, never()).hasCaptainDraftPoint(any(), any());
        verifyNoInteractions(accountPersonalDataRepository, supabaseAuthAdminClient);
    }

    @Test
    void takesBothCaptainsPointsBackWithTheRecord() {
        captainDraftPoints.takeBack(draft(100L, captain(1L, "홈감독", null), captain(2L, "원정감독", AWAY_LOGIN)));

        verify(pointService).reverseCaptainDraftPoints(100L, List.of(1L, 2L));
    }

    private static CaptainDraft draft(Long id, Player home, Player away) {
        CaptainDraft draft = new CaptainDraft();
        draft.setId(id);
        draft.setHomeCaptain(home);
        draft.setAwayCaptain(away);
        return draft;
    }

    private static Player captain(Long id, String nickname, UUID login) {
        Player player = new Player();
        player.setId(id);
        player.setNickname(nickname);
        player.setAuthUserId(login);
        return player;
    }
}
