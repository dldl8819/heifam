package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.domain.AdminMmrAccessEmail;
import com.balancify.backend.domain.AllowedUserEmail;
import com.balancify.backend.domain.ManagedAdminEmail;
import com.balancify.backend.domain.MatchResultEditorEmail;
import com.balancify.backend.domain.UserRacePreference;
import com.balancify.backend.repository.AdminMmrAccessEmailRepository;
import com.balancify.backend.repository.AllowedUserEmailRepository;
import com.balancify.backend.repository.ManagedAdminEmailRepository;
import com.balancify.backend.repository.MatchResultEditorEmailRepository;
import com.balancify.backend.repository.UserRacePreferenceRepository;
import com.balancify.backend.security.AdminKeyProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessControlServiceTest {

    @Mock
    private ManagedAdminEmailRepository managedAdminEmailRepository;

    @Mock
    private AdminMmrAccessEmailRepository adminMmrAccessEmailRepository;

    @Mock
    private AllowedUserEmailRepository allowedUserEmailRepository;

    @Mock
    private UserRacePreferenceRepository userRacePreferenceRepository;

    @Mock
    private MatchResultEditorEmailRepository matchResultEditorEmailRepository;

    private AccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        AdminKeyProperties adminKeyProperties = new AdminKeyProperties();
        adminKeyProperties.setEmails("ops@hei.gg");
        adminKeyProperties.setSuperEmails("superadmin@hei.gg");
        adminKeyProperties.setAllowedEmails("member@hei.gg");

        when(managedAdminEmailRepository.findAllByOrderByNormalizedEmailAsc())
            .thenReturn(List.of());
        when(allowedUserEmailRepository.findAllByOrderByNormalizedEmailAsc())
            .thenReturn(List.of());
        when(managedAdminEmailRepository.findByNormalizedEmail(anyString()))
            .thenReturn(Optional.empty());
        when(adminMmrAccessEmailRepository.findByNormalizedEmail(anyString()))
            .thenReturn(Optional.empty());
        when(allowedUserEmailRepository.findByNormalizedEmail(anyString()))
            .thenReturn(Optional.empty());
        when(userRacePreferenceRepository.findByNormalizedEmail(anyString()))
            .thenReturn(Optional.empty());

        accessControlService = new AccessControlService(
            adminKeyProperties,
            managedAdminEmailRepository,
            adminMmrAccessEmailRepository,
            allowedUserEmailRepository,
            userRacePreferenceRepository,
            matchResultEditorEmailRepository,
            60_000L
        );
    }

    private static AllowedUserEmail allowedUser(String email, String nickname) {
        AllowedUserEmail row = new AllowedUserEmail();
        row.setEmail(email);
        row.setNormalizedEmail(email);
        row.setNickname(nickname);
        return row;
    }

    private static ManagedAdminEmail managedAdmin(String email, String nickname) {
        ManagedAdminEmail row = new ManagedAdminEmail();
        row.setEmail(email);
        row.setNormalizedEmail(email);
        row.setNickname(nickname);
        return row;
    }

    @Test
    void readsTheAccessListsInAFewQueriesHoweverLongTheyAre() {
        List<AllowedUserEmail> allowed = new ArrayList<>();
        for (int index = 1; index <= 68; index++) {
            allowed.add(allowedUser("member-" + index + "@hei.gg", "Member" + index));
        }
        allowed.add(allowedUser("promoted@hei.gg", "OldNickname"));
        when(allowedUserEmailRepository.findAllByOrderByNormalizedEmailAsc()).thenReturn(allowed);
        when(managedAdminEmailRepository.findAllByOrderByNormalizedEmailAsc())
            .thenReturn(List.of(managedAdmin("promoted@hei.gg", "AdminNickname"), managedAdmin("viewer@hei.gg", "Viewer")));
        AdminMmrAccessEmail mmrAccess = new AdminMmrAccessEmail();
        mmrAccess.setEmail("viewer@hei.gg");
        mmrAccess.setNormalizedEmail("viewer@hei.gg");
        when(adminMmrAccessEmailRepository.findAll()).thenReturn(List.of(mmrAccess));
        MatchResultEditorEmail editor = new MatchResultEditorEmail();
        editor.setEmail("member-3@hei.gg");
        editor.setNormalizedEmail("member-3@hei.gg");
        when(matchResultEditorEmailRepository.findAllByOrderByNormalizedEmailAsc()).thenReturn(List.of(editor));

        AccessControlService.AllowedEmailSnapshot allowedSnapshot = accessControlService.getAllowedEmailSnapshot();
        AccessControlService.AdminEmailSnapshot adminSnapshot = accessControlService.getAdminEmailSnapshot();
        List<AccessControlService.AccessEmailEntry> editors = accessControlService.getMatchResultEditors();

        // The member allowed in the settings, and the 69 rows; an admin entry's nickname wins.
        assertThat(allowedSnapshot.allowedUsers()).hasSize(70);
        assertThat(allowedSnapshot.allowedUsers())
            .filteredOn(entry -> List.of("member-7@hei.gg", "promoted@hei.gg", "member@hei.gg").contains(entry.email()))
            .extracting(AccessControlService.AccessEmailEntry::email, AccessControlService.AccessEmailEntry::nickname)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("member-7@hei.gg", "Member7"),
                org.assertj.core.groups.Tuple.tuple("member@hei.gg", null),
                org.assertj.core.groups.Tuple.tuple("promoted@hei.gg", "AdminNickname")
            );
        assertThat(adminSnapshot.superAdmins())
            .containsExactly(new AccessControlService.AccessEmailEntry("superadmin@hei.gg", null, true));
        assertThat(adminSnapshot.admins()).containsExactly(
            new AccessControlService.AccessEmailEntry("ops@hei.gg", null, false),
            new AccessControlService.AccessEmailEntry("promoted@hei.gg", "AdminNickname", false),
            new AccessControlService.AccessEmailEntry("viewer@hei.gg", "Viewer", true)
        );
        assertThat(editors).containsExactly(new AccessControlService.AccessEmailEntry("member-3@hei.gg", "Member3", false));

        // No lookup per row: the lists are read whole, whatever their length.
        verify(managedAdminEmailRepository, never()).findByNormalizedEmail(anyString());
        verify(allowedUserEmailRepository, never()).findByNormalizedEmail(anyString());
        verify(adminMmrAccessEmailRepository, never()).findByNormalizedEmail(anyString());
        verify(userRacePreferenceRepository, never()).findByNormalizedEmail(anyString());
    }

    @Test
    void readsNoNicknamesForAnEmptyListOfResultEditors() {
        when(matchResultEditorEmailRepository.findAllByOrderByNormalizedEmailAsc()).thenReturn(List.of());

        assertThat(accessControlService.getMatchResultEditors()).isEmpty();

        verify(allowedUserEmailRepository, never()).findAllByOrderByNormalizedEmailAsc();
    }

    @Test
    void resolvesSuperAdminProfile() {
        AccessControlService.AccessProfile profile = accessControlService.resolveAccessProfile(
            "superadmin@hei.gg"
        );

        assertThat(profile.superAdmin()).isTrue();
        assertThat(profile.admin()).isTrue();
        assertThat(profile.allowed()).isTrue();
        assertThat(profile.canViewMmr()).isTrue();
        assertThat(profile.role()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void configuredAdminDoesNotViewMmrByDefault() {
        AccessControlService.AccessProfile profile = accessControlService.resolveAccessProfile("ops@hei.gg");

        assertThat(profile.admin()).isTrue();
        assertThat(profile.canViewMmr()).isFalse();
    }

    @Test
    void superAdminTakesPrecedenceWhenEmailAlsoExistsAsAdmin() {
        AdminKeyProperties adminKeyProperties = new AdminKeyProperties();
        adminKeyProperties.setEmails("ops@hei.gg,promoted@hei.gg");
        adminKeyProperties.setSuperEmails("promoted@hei.gg");
        adminKeyProperties.setAllowedEmails("");

        ManagedAdminEmail managedAdminEmail = new ManagedAdminEmail();
        managedAdminEmail.setEmail("promoted@hei.gg");
        managedAdminEmail.setNickname("승격자");

        when(managedAdminEmailRepository.findByNormalizedEmail("promoted@hei.gg"))
            .thenReturn(Optional.of(managedAdminEmail));
        when(allowedUserEmailRepository.findByNormalizedEmail("promoted@hei.gg"))
            .thenReturn(Optional.empty());
        when(userRacePreferenceRepository.findByNormalizedEmail("promoted@hei.gg"))
            .thenReturn(Optional.empty());

        AccessControlService service = new AccessControlService(
            adminKeyProperties,
            managedAdminEmailRepository,
            adminMmrAccessEmailRepository,
            allowedUserEmailRepository,
            userRacePreferenceRepository,
            matchResultEditorEmailRepository,
            60_000L
        );

        AccessControlService.AccessProfile profile = service.resolveAccessProfile("promoted@hei.gg");

        assertThat(profile.superAdmin()).isTrue();
        assertThat(profile.admin()).isTrue();
        assertThat(profile.allowed()).isTrue();
        assertThat(profile.role()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void removingSuperAdminConfigFallsBackToManagedAdminWhenPresent() {
        ManagedAdminEmail managedAdminEmail = new ManagedAdminEmail();
        managedAdminEmail.setEmail("promoted@hei.gg");
        managedAdminEmail.setNickname("승격자");

        when(managedAdminEmailRepository.findByNormalizedEmail("promoted@hei.gg"))
            .thenReturn(Optional.of(managedAdminEmail));
        when(allowedUserEmailRepository.findByNormalizedEmail("promoted@hei.gg"))
            .thenReturn(Optional.empty());
        when(userRacePreferenceRepository.findByNormalizedEmail("promoted@hei.gg"))
            .thenReturn(Optional.empty());

        AdminKeyProperties superAdminProperties = new AdminKeyProperties();
        superAdminProperties.setEmails("");
        superAdminProperties.setSuperEmails("promoted@hei.gg");
        superAdminProperties.setAllowedEmails("");

        AccessControlService superAdminService = new AccessControlService(
            superAdminProperties,
            managedAdminEmailRepository,
            adminMmrAccessEmailRepository,
            allowedUserEmailRepository,
            userRacePreferenceRepository,
            matchResultEditorEmailRepository,
            60_000L
        );

        AdminKeyProperties downgradedProperties = new AdminKeyProperties();
        downgradedProperties.setEmails("");
        downgradedProperties.setSuperEmails("");
        downgradedProperties.setAllowedEmails("");

        AccessControlService downgradedService = new AccessControlService(
            downgradedProperties,
            managedAdminEmailRepository,
            adminMmrAccessEmailRepository,
            allowedUserEmailRepository,
            userRacePreferenceRepository,
            matchResultEditorEmailRepository,
            60_000L
        );

        AccessControlService.AccessProfile beforeRotation = superAdminService.resolveAccessProfile("promoted@hei.gg");
        AccessControlService.AccessProfile afterRotation = downgradedService.resolveAccessProfile("promoted@hei.gg");

        assertThat(beforeRotation.role()).isEqualTo("SUPER_ADMIN");
        assertThat(afterRotation.superAdmin()).isFalse();
        assertThat(afterRotation.admin()).isTrue();
        assertThat(afterRotation.allowed()).isTrue();
        assertThat(afterRotation.canViewMmr()).isFalse();
        assertThat(afterRotation.role()).isEqualTo("ADMIN");
    }

    @Test
    void addsManagedAdminOnlyWhenActorIsSuperAdmin() {
        when(managedAdminEmailRepository.existsByNormalizedEmail("newops@hei.gg")).thenReturn(false);

        accessControlService.addManagedAdminEmail("superadmin@hei.gg", "newops@hei.gg", "운영진");

        verify(managedAdminEmailRepository).save(any(ManagedAdminEmail.class));
    }

    @Test
    void superAdminCanToggleManagedAdminMmrAccess() {
        ManagedAdminEmail managedAdminEmail = new ManagedAdminEmail();
        managedAdminEmail.setEmail("newops@hei.gg");
        managedAdminEmail.setNickname("운영진");
        when(managedAdminEmailRepository.findByNormalizedEmail("newops@hei.gg"))
            .thenReturn(Optional.of(managedAdminEmail));

        accessControlService.updateManagedAdminMmrAccess("superadmin@hei.gg", "newops@hei.gg", true);

        verify(adminMmrAccessEmailRepository).save(any(AdminMmrAccessEmail.class));
        verify(managedAdminEmailRepository, never()).save(managedAdminEmail);
    }

    @Test
    void superAdminCanRevokeManagedAdminMmrAccess() {
        ManagedAdminEmail managedAdminEmail = new ManagedAdminEmail();
        managedAdminEmail.setEmail("newops@hei.gg");
        AdminMmrAccessEmail adminMmrAccessEmail = new AdminMmrAccessEmail();
        adminMmrAccessEmail.setEmail("newops@hei.gg");
        when(managedAdminEmailRepository.findByNormalizedEmail("newops@hei.gg"))
            .thenReturn(Optional.of(managedAdminEmail));
        when(adminMmrAccessEmailRepository.findByNormalizedEmail("newops@hei.gg"))
            .thenReturn(Optional.of(adminMmrAccessEmail));

        accessControlService.updateManagedAdminMmrAccess("superadmin@hei.gg", "newops@hei.gg", false);

        verify(adminMmrAccessEmailRepository).delete(adminMmrAccessEmail);
    }

    @Test
    void rejectsManagedAdminAddWhenActorIsNotSuperAdmin() {
        assertThatThrownBy(() ->
            accessControlService.addManagedAdminEmail("ops@hei.gg", "newops@hei.gg", "운영진")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Only super admins can register operators");

        verify(managedAdminEmailRepository, never()).save(any(ManagedAdminEmail.class));
    }

    @Test
    void allowsAdminToAddAllowedMemberEmail() {
        when(allowedUserEmailRepository.existsByNormalizedEmail("fan@hei.gg")).thenReturn(false);

        accessControlService.addAllowedUserEmail("ops@hei.gg", "fan@hei.gg", "팬");

        verify(allowedUserEmailRepository).save(any(AllowedUserEmail.class));
    }

    @Test
    void resolvesManyNicknamesAtOnceWithAdminNicknamesFirst() {
        AllowedUserEmail member = new AllowedUserEmail();
        member.setNormalizedEmail("fan@hei.gg");
        member.setNickname(" 팬 ");
        AllowedUserEmail promoted = new AllowedUserEmail();
        promoted.setNormalizedEmail("newops@hei.gg");
        promoted.setNickname("예전 닉네임");
        ManagedAdminEmail admin = new ManagedAdminEmail();
        admin.setNormalizedEmail("newops@hei.gg");
        admin.setNickname("운영진");
        when(allowedUserEmailRepository.findAllByOrderByNormalizedEmailAsc()).thenReturn(List.of(member, promoted));
        when(managedAdminEmailRepository.findAllByOrderByNormalizedEmailAsc()).thenReturn(List.of(admin));

        Map<String, String> nicknames = accessControlService.resolveDisplayNicknames(
            List.of("FAN@hei.gg", "newops@hei.gg", "gone@hei.gg")
        );

        assertThat(nicknames)
            .containsEntry("fan@hei.gg", "팬")
            .containsEntry("newops@hei.gg", "운영진")
            .containsEntry("gone@hei.gg", null);
    }

    @Test
    void canRemoveDynamicAllowedEmail() {
        AllowedUserEmail allowedUserEmail = new AllowedUserEmail();
        allowedUserEmail.setEmail("fan@hei.gg");
        when(allowedUserEmailRepository.findByNormalizedEmail("fan@hei.gg"))
            .thenReturn(Optional.of(allowedUserEmail));

        accessControlService.removeAllowedUserEmail("ops@hei.gg", "fan@hei.gg");

        verify(allowedUserEmailRepository).delete(allowedUserEmail);
    }

    @Test
    void marksAMemberWithTheEditorRowAsAMatchResultEditor() {
        when(matchResultEditorEmailRepository.findByNormalizedEmail("member@hei.gg"))
            .thenReturn(Optional.of(editorRow("member@hei.gg")));

        AccessControlService.AccessProfile profile = accessControlService.resolveAccessProfile("member@hei.gg");

        assertThat(profile.matchResultEditor()).isTrue();
        assertThat(profile.role()).isEqualTo("MEMBER");
        assertThat(accessControlService.isMatchResultEditor("member@hei.gg")).isTrue();
        assertThat(accessControlService.resolveActorRole("member@hei.gg"))
            .isEqualTo(AccessControlService.ACTOR_ROLE_RESULT_EDITOR);
    }

    @Test
    void neverTreatsAnAdminAsAMatchResultEditor() {
        when(matchResultEditorEmailRepository.findByNormalizedEmail("ops@hei.gg"))
            .thenReturn(Optional.of(editorRow("ops@hei.gg")));

        assertThat(accessControlService.isMatchResultEditor("ops@hei.gg")).isFalse();
        assertThat(accessControlService.resolveActorRole("ops@hei.gg")).isEqualTo(AccessControlService.ACTOR_ROLE_ADMIN);
        assertThat(accessControlService.resolveActorRole("superadmin@hei.gg"))
            .isEqualTo(AccessControlService.ACTOR_ROLE_SUPER_ADMIN);
        assertThat(accessControlService.resolveActorRole("member@hei.gg"))
            .isEqualTo(AccessControlService.ACTOR_ROLE_MEMBER);
        assertThat(accessControlService.resolveActorRole("stranger@hei.gg")).isNull();
    }

    @Test
    void superAdminGrantsMatchResultEditingToARegisteredMember() {
        accessControlService.addMatchResultEditor("superadmin@hei.gg", "member@hei.gg");

        verify(matchResultEditorEmailRepository).save(argThat(
            editor -> "member@hei.gg".equals(editor.getEmail())
                && "superadmin@hei.gg".equals(editor.getCreatedByEmail())
        ));
    }

    @Test
    void refusesMatchResultEditingGrantsThatDoNotFit() {
        assertThatThrownBy(() -> accessControlService.addMatchResultEditor("ops@hei.gg", "member@hei.gg"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accessControlService.addMatchResultEditor("superadmin@hei.gg", "ops@hei.gg"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accessControlService.addMatchResultEditor("superadmin@hei.gg", "stranger@hei.gg"))
            .isInstanceOf(IllegalArgumentException.class);

        verify(matchResultEditorEmailRepository, never()).save(any(MatchResultEditorEmail.class));
    }

    @Test
    void removingAMemberAlsoRevokesTheirMatchResultEditing() {
        AllowedUserEmail allowedUserEmail = new AllowedUserEmail();
        allowedUserEmail.setEmail("fan@hei.gg");
        MatchResultEditorEmail editor = editorRow("fan@hei.gg");
        when(allowedUserEmailRepository.findByNormalizedEmail("fan@hei.gg"))
            .thenReturn(Optional.of(allowedUserEmail));
        when(matchResultEditorEmailRepository.findByNormalizedEmail("fan@hei.gg"))
            .thenReturn(Optional.of(editor));

        accessControlService.removeAllowedUserEmail("ops@hei.gg", "fan@hei.gg");

        verify(matchResultEditorEmailRepository).delete(editor);
    }

    private MatchResultEditorEmail editorRow(String email) {
        MatchResultEditorEmail editor = new MatchResultEditorEmail();
        editor.setEmail(email);
        editor.setNormalizedEmail(email);
        return editor;
    }

    @Test
    void allowsAdminToUpdateAllowedMemberNickname() {
        AllowedUserEmail allowedUserEmail = new AllowedUserEmail();
        allowedUserEmail.setEmail("fan@hei.gg");
        allowedUserEmail.setNickname("팬");
        when(allowedUserEmailRepository.findByNormalizedEmail("fan@hei.gg"))
            .thenReturn(Optional.of(allowedUserEmail));

        accessControlService.updateAllowedUserEmailNickname("ops@hei.gg", "fan@hei.gg", "새닉네임");

        assertThat(allowedUserEmail.getNickname()).isEqualTo("새닉네임");
        verify(allowedUserEmailRepository).save(allowedUserEmail);
    }

    @Test
    void rejectsAllowedNicknameUpdateWhenActorIsNotAdmin() {
        assertThatThrownBy(() ->
            accessControlService.updateAllowedUserEmailNickname("fan@hei.gg", "fan@hei.gg", "새닉네임")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Only admins can update allowed member nicknames");

        verify(allowedUserEmailRepository, never()).save(any(AllowedUserEmail.class));
    }

    @Test
    void rejectsAllowedNicknameUpdateWhenTargetIsNotRegistered() {
        when(allowedUserEmailRepository.findByNormalizedEmail("ghost@hei.gg"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            accessControlService.updateAllowedUserEmailNickname("ops@hei.gg", "ghost@hei.gg", "새닉네임")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Target email is not registered");

        verify(allowedUserEmailRepository, never()).save(any(AllowedUserEmail.class));
    }

    @Test
    void rejectsAllowedNicknameUpdateWhenTargetIsAdmin() {
        assertThatThrownBy(() ->
            accessControlService.updateAllowedUserEmailNickname("superadmin@hei.gg", "ops@hei.gg", "새닉네임")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Admin email nickname cannot be updated here");

        verify(allowedUserEmailRepository, never()).save(any(AllowedUserEmail.class));
    }

    @Test
    void resolvesPreferredRaceWhenStored() {
        UserRacePreference preference = new UserRacePreference();
        preference.setEmail("member@hei.gg");
        preference.setPreferredRace("PT");
        when(userRacePreferenceRepository.findByNormalizedEmail("member@hei.gg"))
            .thenReturn(Optional.of(preference));

        AccessControlService.AccessProfile profile = accessControlService.resolveAccessProfile("member@hei.gg");

        assertThat(profile.preferredRace()).isEqualTo("PT");
    }

    @Test
    void resolvesNicknameFromAllowlistWhenStored() {
        AllowedUserEmail allowedUserEmail = new AllowedUserEmail();
        allowedUserEmail.setEmail("member@hei.gg");
        allowedUserEmail.setNickname("민식");
        when(allowedUserEmailRepository.findByNormalizedEmail("member@hei.gg"))
            .thenReturn(Optional.of(allowedUserEmail));

        AccessControlService.AccessProfile profile = accessControlService.resolveAccessProfile("member@hei.gg");

        assertThat(profile.nickname()).isEqualTo("민식");
    }

    @Test
    void cachesAccessProfileLookupsForRepeatedRequests() {
        accessControlService.resolveAccessProfile("member@hei.gg");
        accessControlService.resolveAccessProfile("member@hei.gg");

        verify(managedAdminEmailRepository, times(1)).findByNormalizedEmail("member@hei.gg");
        verify(allowedUserEmailRepository, times(1)).findByNormalizedEmail("member@hei.gg");
        verify(userRacePreferenceRepository, times(1)).findByNormalizedEmail("member@hei.gg");
    }

    @Test
    void refreshesAccessProfileAfterCacheExpiry() throws InterruptedException {
        AdminKeyProperties adminKeyProperties = new AdminKeyProperties();
        adminKeyProperties.setAllowedEmails("");
        AtomicInteger lookupCount = new AtomicInteger();

        when(managedAdminEmailRepository.findByNormalizedEmail("fan@hei.gg")).thenReturn(Optional.empty());
        when(userRacePreferenceRepository.findByNormalizedEmail("fan@hei.gg")).thenReturn(Optional.empty());
        when(allowedUserEmailRepository.findByNormalizedEmail("fan@hei.gg")).thenAnswer(invocation -> {
            if (lookupCount.incrementAndGet() == 1) {
                return Optional.empty();
            }
            AllowedUserEmail allowedUserEmail = new AllowedUserEmail();
            allowedUserEmail.setEmail("fan@hei.gg");
            allowedUserEmail.setNickname("팬");
            return Optional.of(allowedUserEmail);
        });

        AccessControlService serviceWithShortCache = new AccessControlService(
            adminKeyProperties,
            managedAdminEmailRepository,
            adminMmrAccessEmailRepository,
            allowedUserEmailRepository,
            userRacePreferenceRepository,
            matchResultEditorEmailRepository,
            5L
        );

        AccessControlService.AccessProfile first = serviceWithShortCache.resolveAccessProfile("fan@hei.gg");
        Thread.sleep(20L);
        AccessControlService.AccessProfile second = serviceWithShortCache.resolveAccessProfile("fan@hei.gg");

        assertThat(first.allowed()).isFalse();
        assertThat(second.allowed()).isTrue();
        verify(allowedUserEmailRepository, times(2)).findByNormalizedEmail("fan@hei.gg");
    }

    @Test
    void invalidatesCachedAccessProfileWhenPreferredRaceChanges() {
        AccessControlService.AccessProfile initialProfile = accessControlService.resolveAccessProfile("member@hei.gg");

        UserRacePreference preference = new UserRacePreference();
        preference.setEmail("member@hei.gg");
        preference.setPreferredRace("TZ");
        when(userRacePreferenceRepository.findByNormalizedEmail("member@hei.gg"))
            .thenReturn(Optional.of(preference));

        AccessControlService.AccessProfile updatedProfile = accessControlService.upsertPreferredRace("member@hei.gg", "TZ");
        AccessControlService.AccessProfile reloadedProfile = accessControlService.resolveAccessProfile("member@hei.gg");

        assertThat(initialProfile.preferredRace()).isNull();
        assertThat(updatedProfile.preferredRace()).isEqualTo("TZ");
        assertThat(reloadedProfile.preferredRace()).isEqualTo("TZ");
        verify(userRacePreferenceRepository, times(3)).findByNormalizedEmail("member@hei.gg");
    }

    @Test
    void savesPreferredRaceForCurrentUser() {
        when(userRacePreferenceRepository.findByNormalizedEmail("member@hei.gg"))
            .thenReturn(Optional.empty());

        accessControlService.upsertPreferredRace("member@hei.gg", "tz");

        verify(userRacePreferenceRepository).save(any(UserRacePreference.class));
    }

    @Test
    void rejectsUnsupportedPreferredRaceValue() {
        assertThatThrownBy(() -> accessControlService.upsertPreferredRace("member@hei.gg", "R"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid race");
    }
}
