package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.domain.AllowedUserEmail;
import com.balancify.backend.domain.ManagedAdminEmail;
import com.balancify.backend.domain.UserRacePreference;
import com.balancify.backend.repository.AllowedUserEmailRepository;
import com.balancify.backend.repository.ManagedAdminEmailRepository;
import com.balancify.backend.repository.UserRacePreferenceRepository;
import com.balancify.backend.security.AdminKeyProperties;
import java.util.List;
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
    private AllowedUserEmailRepository allowedUserEmailRepository;

    @Mock
    private UserRacePreferenceRepository userRacePreferenceRepository;

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
        when(allowedUserEmailRepository.findByNormalizedEmail(anyString()))
            .thenReturn(Optional.empty());
        when(userRacePreferenceRepository.findByNormalizedEmail(anyString()))
            .thenReturn(Optional.empty());

        accessControlService = new AccessControlService(
            adminKeyProperties,
            managedAdminEmailRepository,
            allowedUserEmailRepository,
            userRacePreferenceRepository,
            60_000L
        );
    }

    @Test
    void resolvesSuperAdminProfile() {
        AccessControlService.AccessProfile profile = accessControlService.resolveAccessProfile(
            "superadmin@hei.gg"
        );

        assertThat(profile.superAdmin()).isTrue();
        assertThat(profile.admin()).isTrue();
        assertThat(profile.allowed()).isTrue();
        assertThat(profile.role()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void addsManagedAdminOnlyWhenActorIsSuperAdmin() {
        when(managedAdminEmailRepository.existsByNormalizedEmail("newops@hei.gg")).thenReturn(false);

        accessControlService.addManagedAdminEmail("superadmin@hei.gg", "newops@hei.gg", "운영진");

        verify(managedAdminEmailRepository).save(any(ManagedAdminEmail.class));
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
    void canRemoveDynamicAllowedEmail() {
        AllowedUserEmail allowedUserEmail = new AllowedUserEmail();
        allowedUserEmail.setEmail("fan@hei.gg");
        when(allowedUserEmailRepository.findByNormalizedEmail("fan@hei.gg"))
            .thenReturn(Optional.of(allowedUserEmail));

        accessControlService.removeAllowedUserEmail("ops@hei.gg", "fan@hei.gg");

        verify(allowedUserEmailRepository).delete(allowedUserEmail);
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
            allowedUserEmailRepository,
            userRacePreferenceRepository,
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
        assertThatThrownBy(() -> accessControlService.upsertPreferredRace("member@hei.gg", "PTZ"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid race");
    }
}
