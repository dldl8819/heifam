package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.repository.AccessPersonMergeRepository;
import com.balancify.backend.repository.AccessPersonRepository;
import com.balancify.backend.repository.AccessPersonRepository.PersonEmailRow;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessPersonServiceTest {

    private static final String A = "minsik-1@example.com";
    private static final String B = "minsik-2@example.com";
    private static final String C = "minsik-3@example.com";
    private static final String W1 = "water-1@example.com";
    private static final String W2 = "water-2@example.com";
    private static final String SOLO = "solo@example.com";
    private static final String ACTOR = "superadmin@example.com";

    @Mock
    private AccessPersonRepository accessPersonRepository;

    @Mock
    private AccessPersonMergeRepository accessPersonMergeRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    private AccessPersonService service;

    @BeforeEach
    void setUp() {
        when(accessControlService.getAdminEmailSnapshot()).thenReturn(new AccessControlService.AdminEmailSnapshot(
            List.of(entry(A, "민식")),
            List.of(entry(W1, "워터"))
        ));
        when(accessControlService.getAllowedEmailSnapshot()).thenReturn(new AccessControlService.AllowedEmailSnapshot(List.of(
            entry(B, "민식(2)"),
            entry(C, "민식 (3)"),
            entry(W2, "워터(2)"),
            entry(SOLO, "혼자"),
            entry("nameless@example.com", null)
        )));
        when(accessPersonRepository.listAll()).thenReturn(List.of());
        when(accessPersonRepository.personIdsOf(anyCollection())).thenReturn(Map.of());
        when(accessPersonMergeRepository.merge(anyLong())).thenReturn(new AccessPersonMergeRepository.MergeOutcome(0, 0, 0));
        service = new AccessPersonService(accessPersonRepository, accessPersonMergeRepository, accessControlService, operationAuditLogService);
    }

    @Test
    void readsTheNameOutOfANumberedNickname() {
        assertThat(AccessPersonService.baseName("민식(2)")).isEqualTo("민식");
        assertThat(AccessPersonService.baseName(" 민식 ( 3 ) ")).isEqualTo("민식");
        assertThat(AccessPersonService.baseName("워터")).isEqualTo("워터");
        assertThat(AccessPersonService.baseName("(2)")).isEqualTo("(2)");
        assertThat(AccessPersonService.baseName("A(b)")).isEqualTo("A(b)");
        assertThat(AccessPersonService.baseName(null)).isEmpty();
    }

    @Test
    void suggestsEmailsWhoseNicknamesAreOneNameButGroupsNothingByItself() {
        AccessPersonService.Overview overview = service.overview();

        assertThat(overview.people()).isEmpty();
        assertThat(overview.suggestions()).extracting(AccessPersonService.Suggestion::displayName).containsExactly("민식", "워터");
        assertThat(overview.suggestions().getFirst().members()).extracting(AccessPersonService.Member::email).containsExactly(A, C, B);
        assertThat(overview.suggestions().getFirst().personId()).isNull();
        assertThat(overview.candidates()).hasSize(7);
        verify(accessPersonRepository, never()).createPerson(anyString(), any());
    }

    @Test
    void suggestsAddingTheRestToAPersonAlreadyMadeAndNothingOnceAllAreIn() {
        when(accessPersonRepository.listAll()).thenReturn(List.of(new PersonEmailRow(5L, "민식", A), new PersonEmailRow(5L, "민식", B)));
        AccessPersonService.Suggestion minsik = service.overview().suggestions().getFirst();
        assertThat(minsik.personId()).isEqualTo(5L);
        assertThat(minsik.members()).extracting(AccessPersonService.Member::email).containsExactly(A, C, B);

        when(accessPersonRepository.listAll()).thenReturn(List.of(
            new PersonEmailRow(5L, "민식", A), new PersonEmailRow(5L, "민식", B), new PersonEmailRow(5L, "민식", C)
        ));
        AccessPersonService.Overview overview = service.overview();
        assertThat(overview.suggestions()).extracting(AccessPersonService.Suggestion::displayName).containsExactly("워터");
        assertThat(overview.people()).hasSize(1);
        assertThat(overview.people().getFirst().members()).extracting(AccessPersonService.Member::hasAccess).containsOnly(true);
    }

    @Test
    void groupsChosenEmailsAsANewPersonMergesWhatTheyDidAndLogsIt() {
        when(accessPersonRepository.createPerson("민식", ACTOR)).thenReturn(7L);
        when(accessPersonRepository.countEmails(7L)).thenReturn(3);
        when(accessPersonMergeRepository.merge(7L)).thenReturn(new AccessPersonMergeRepository.MergeOutcome(2, 1, 5));

        service.group(List.of(" Minsik-1@Example.com ", B, C, B), " ", ACTOR, "Owner");

        verify(accessPersonRepository).attach(eq(7L), eq(java.util.Set.of(A, B, C)));
        verify(accessPersonRepository).rename(7L, "민식");
        verify(accessPersonMergeRepository).merge(7L);
        verify(operationAuditLogService).recordAccessPeople(
            OperationAuditLogService.ACTION_ACCESS_PEOPLE_GROUPED, ACTOR, "Owner", 7L, "민식",
            "emails=3, duplicatesTidied=2, predictionsTidied=1, pointsGivenBack=5"
        );
    }

    @Test
    void joinsTwoPeopleIntoTheOlderOne() {
        when(accessPersonRepository.personIdsOf(anyCollection())).thenReturn(Map.of(A, 3L, W1, 9L));
        when(accessPersonRepository.listAll()).thenReturn(List.of(
            new PersonEmailRow(3L, "민식", A), new PersonEmailRow(3L, "민식", B),
            new PersonEmailRow(9L, "워터", W1), new PersonEmailRow(9L, "워터", W2)
        ));

        service.group(List.of(A, W1), "한 사람", ACTOR, null);

        verify(accessPersonRepository).lockPeople(eq(new java.util.TreeSet<>(List.of(3L, 9L))));
        verify(accessPersonRepository).attach(eq(3L), eq(List.of(W1, W2)));
        verify(accessPersonRepository).deletePerson(9L);
        verify(accessPersonRepository).rename(3L, "한 사람");
        verify(accessPersonRepository, never()).createPerson(anyString(), any());
    }

    @Test
    void refusesFewerThanTwoEmailsAnEmailWithoutAccessAndATooLongName() {
        assertThatThrownBy(() -> service.group(List.of(A, " minsik-1@example.com"), null, ACTOR, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("두 개 이상");
        assertThatThrownBy(() -> service.group(List.of(A, "stranger@example.com"), null, ACTOR, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("접속 권한이 없는");
        assertThatThrownBy(() -> service.group(List.of(A, B), "가".repeat(101), ACTOR, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("100자");
        verify(accessPersonRepository, never()).attach(anyLong(), anyCollection());
    }

    @Test
    void takesAnEmailOutAndEndsAPersonLeftWithOne() {
        when(accessPersonRepository.personIdsOf(List.of(B))).thenReturn(Map.of(B, 5L));
        when(accessPersonRepository.displayNameOf(5L)).thenReturn(Optional.of("민식"));
        when(accessPersonRepository.countEmails(5L)).thenReturn(1);

        service.ungroup(" Minsik-2@example.com ", ACTOR, null);

        verify(accessPersonRepository).detach(B);
        verify(accessPersonRepository).deletePerson(5L);
        verify(operationAuditLogService).recordAccessPeople(
            OperationAuditLogService.ACTION_ACCESS_PEOPLE_UNGROUPED, ACTOR, null, 5L, "민식", null
        );
        assertThatThrownBy(() -> service.ungroup(SOLO, ACTOR, null)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void renamesAPersonButNotToNothing() {
        when(accessPersonRepository.displayNameOf(5L)).thenReturn(Optional.of("민식"));

        service.rename(5L, " 민식이 ", ACTOR, null);

        verify(accessPersonRepository).rename(5L, "민식이");
        assertThatThrownBy(() -> service.rename(5L, "  ", ACTOR, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.rename(6L, "누구", ACTOR, null)).isInstanceOf(NoSuchElementException.class);
    }

    private static AccessControlService.AccessEmailEntry entry(String email, String nickname) {
        return new AccessControlService.AccessEmailEntry(email, nickname, false);
    }
}
