package com.balancify.backend.service;

import com.balancify.backend.domain.AllowedUserEmail;
import com.balancify.backend.domain.ManagedAdminEmail;
import com.balancify.backend.domain.UserRacePreference;
import com.balancify.backend.repository.AllowedUserEmailRepository;
import com.balancify.backend.repository.ManagedAdminEmailRepository;
import com.balancify.backend.repository.UserRacePreferenceRepository;
import com.balancify.backend.security.AdminKeyProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessControlService {

    private final AdminKeyProperties adminKeyProperties;
    private final ManagedAdminEmailRepository managedAdminEmailRepository;
    private final AllowedUserEmailRepository allowedUserEmailRepository;
    private final UserRacePreferenceRepository userRacePreferenceRepository;
    private final ConcurrentMap<String, CachedAccessState> accessStateCache = new ConcurrentHashMap<>();

    private static final Set<String> ALLOWED_RACES = Set.of("P", "T", "Z", "PT", "PZ", "TZ", "R");
    private static final int MAX_NICKNAME_LENGTH = 100;
    private static final long ACCESS_STATE_CACHE_TTL_MS = 60_000L;

    public AccessControlService(
        AdminKeyProperties adminKeyProperties,
        ManagedAdminEmailRepository managedAdminEmailRepository,
        AllowedUserEmailRepository allowedUserEmailRepository,
        UserRacePreferenceRepository userRacePreferenceRepository
    ) {
        this.adminKeyProperties = adminKeyProperties;
        this.managedAdminEmailRepository = managedAdminEmailRepository;
        this.allowedUserEmailRepository = allowedUserEmailRepository;
        this.userRacePreferenceRepository = userRacePreferenceRepository;
    }

    @Transactional(readOnly = true)
    public AccessProfile resolveAccessProfile(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            return new AccessProfile("", null, "BLOCKED", false, false, false, null);
        }

        AccessState accessState = resolveAccessState(normalizedEmail);
        boolean superAdmin = accessState.superAdmin();
        boolean admin = accessState.admin();
        boolean allowed = accessState.allowed();
        String role = superAdmin ? "SUPER_ADMIN" : admin ? "ADMIN" : allowed ? "MEMBER" : "BLOCKED";

        return new AccessProfile(
            normalizedEmail,
            accessState.nickname(),
            role,
            admin,
            superAdmin,
            allowed,
            accessState.preferredRace()
        );
    }

    @Transactional
    public AccessProfile upsertPreferredRace(String email, String race) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            throw new IllegalArgumentException("A valid email is required");
        }

        String normalizedRace = normalizeRace(race);
        if (!ALLOWED_RACES.contains(normalizedRace)) {
            throw new IllegalArgumentException("Invalid race. Allowed values: P, T, Z, PT, PZ, TZ, R");
        }

        UserRacePreference preference = userRacePreferenceRepository
            .findByNormalizedEmail(normalizedEmail)
            .orElseGet(UserRacePreference::new);
        preference.setEmail(normalizedEmail);
        preference.setPreferredRace(normalizedRace);
        userRacePreferenceRepository.save(preference);
        invalidateAccessState(normalizedEmail);

        return resolveAccessProfile(normalizedEmail);
    }

    @Transactional(readOnly = true)
    public boolean isSuperAdminEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return !normalizedEmail.isEmpty() && adminKeyProperties.isConfiguredSuperAdminEmail(normalizedEmail);
    }

    @Transactional(readOnly = true)
    public boolean isAdminEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return !normalizedEmail.isEmpty() && resolveAccessState(normalizedEmail).admin();
    }

    @Transactional(readOnly = true)
    public boolean isServiceAccessAllowed(String email) {
        String normalizedEmail = normalizeEmail(email);
        return !normalizedEmail.isEmpty() && resolveAccessState(normalizedEmail).allowed();
    }

    @Transactional(readOnly = true)
    public AdminEmailSnapshot getAdminEmailSnapshot() {
        List<String> superAdminEmails = adminKeyProperties
            .getNormalizedSuperAdminEmails()
            .stream()
            .sorted()
            .toList();

        List<AccessEmailEntry> superAdmins = superAdminEmails.stream()
            .map(superAdminEmail -> new AccessEmailEntry(superAdminEmail, resolveNickname(superAdminEmail)))
            .toList();

        Set<String> adminSet = new LinkedHashSet<>(adminKeyProperties.getNormalizedAdminEmails());
        adminSet.addAll(
            managedAdminEmailRepository.findAllByOrderByNormalizedEmailAsc()
                .stream()
                .map(ManagedAdminEmail::getNormalizedEmail)
                .toList()
        );
        adminSet.removeAll(superAdminEmails);

        List<AccessEmailEntry> admins = adminSet.stream()
            .sorted()
            .map(adminEmail -> new AccessEmailEntry(adminEmail, resolveNickname(adminEmail)))
            .toList();

        return new AdminEmailSnapshot(superAdmins, admins);
    }

    @Transactional(readOnly = true)
    public AllowedEmailSnapshot getAllowedEmailSnapshot() {
        Set<String> allowedSet = new LinkedHashSet<>(adminKeyProperties.getNormalizedAllowedEmails());
        allowedSet.addAll(
            allowedUserEmailRepository.findAllByOrderByNormalizedEmailAsc()
                .stream()
                .map(AllowedUserEmail::getNormalizedEmail)
                .toList()
        );

        List<AccessEmailEntry> allowedUsers = new ArrayList<>(allowedSet).stream()
            .sorted()
            .map(allowedEmail -> new AccessEmailEntry(allowedEmail, resolveNickname(allowedEmail)))
            .toList();

        return new AllowedEmailSnapshot(allowedUsers);
    }

    @Transactional
    public AdminEmailSnapshot addManagedAdminEmail(String actorEmail, String targetEmail, String targetNickname) {
        String normalizedActorEmail = normalizeEmail(actorEmail);
        String normalizedTargetEmail = normalizeEmail(targetEmail);
        String normalizedTargetNickname = normalizeNickname(targetNickname);
        validateEmail(normalizedTargetEmail);
        validateNickname(normalizedTargetNickname);

        if (!isSuperAdminEmail(normalizedActorEmail)) {
            throw new IllegalArgumentException("Only super admins can register operators");
        }
        if (adminKeyProperties.isConfiguredSuperAdminEmail(normalizedTargetEmail)) {
            throw new IllegalArgumentException("Target email is already a super admin");
        }
        upsertManagedAdminEmail(normalizedActorEmail, normalizedTargetEmail, normalizedTargetNickname);
        invalidateAccessState(normalizedTargetEmail);

        return getAdminEmailSnapshot();
    }

    @Transactional
    public AdminEmailSnapshot removeManagedAdminEmail(String actorEmail, String targetEmail) {
        String normalizedActorEmail = normalizeEmail(actorEmail);
        String normalizedTargetEmail = normalizeEmail(targetEmail);
        validateEmail(normalizedTargetEmail);

        if (!isSuperAdminEmail(normalizedActorEmail)) {
            throw new IllegalArgumentException("Only super admins can remove operators");
        }
        if (adminKeyProperties.isConfiguredSuperAdminEmail(normalizedTargetEmail)) {
            throw new IllegalArgumentException("Super admin email cannot be removed");
        }
        if (adminKeyProperties.isConfiguredAdminEmail(normalizedTargetEmail)) {
            throw new IllegalArgumentException("Configured admin email cannot be removed");
        }

        managedAdminEmailRepository
            .findByNormalizedEmail(normalizedTargetEmail)
            .ifPresent(managedAdminEmailRepository::delete);
        invalidateAccessState(normalizedTargetEmail);

        return getAdminEmailSnapshot();
    }

    @Transactional
    public AllowedEmailSnapshot addAllowedUserEmail(String actorEmail, String targetEmail, String targetNickname) {
        String normalizedActorEmail = normalizeEmail(actorEmail);
        String normalizedTargetEmail = normalizeEmail(targetEmail);
        String normalizedTargetNickname = normalizeNickname(targetNickname);
        validateEmail(normalizedTargetEmail);
        validateNickname(normalizedTargetNickname);

        if (!isAdminEmail(normalizedActorEmail)) {
            throw new IllegalArgumentException("Only admins can register allowed member emails");
        }

        if (isAdminEmail(normalizedTargetEmail)) {
            return getAllowedEmailSnapshot();
        }
        upsertAllowedUserEmail(normalizedActorEmail, normalizedTargetEmail, normalizedTargetNickname);
        invalidateAccessState(normalizedTargetEmail);

        return getAllowedEmailSnapshot();
    }

    @Transactional
    public AllowedEmailSnapshot removeAllowedUserEmail(String actorEmail, String targetEmail) {
        String normalizedActorEmail = normalizeEmail(actorEmail);
        String normalizedTargetEmail = normalizeEmail(targetEmail);
        validateEmail(normalizedTargetEmail);

        if (!isAdminEmail(normalizedActorEmail)) {
            throw new IllegalArgumentException("Only admins can remove allowed member emails");
        }
        if (isAdminEmail(normalizedTargetEmail)) {
            throw new IllegalArgumentException("Admin email access cannot be removed");
        }
        if (adminKeyProperties.isConfiguredAllowedEmail(normalizedTargetEmail)) {
            throw new IllegalArgumentException("Configured allowed email cannot be removed");
        }

        allowedUserEmailRepository
            .findByNormalizedEmail(normalizedTargetEmail)
            .ifPresent(allowedUserEmailRepository::delete);
        invalidateAccessState(normalizedTargetEmail);

        return getAllowedEmailSnapshot();
    }

    private AccessState resolveAccessState(String normalizedEmail) {
        long now = System.currentTimeMillis();
        CachedAccessState cached = accessStateCache.get(normalizedEmail);
        if (cached != null && cached.expiresAtEpochMs() > now) {
            return cached.accessState();
        }

        AccessState computed = loadAccessState(normalizedEmail);
        accessStateCache.put(
            normalizedEmail,
            new CachedAccessState(computed, now + ACCESS_STATE_CACHE_TTL_MS)
        );
        return computed;
    }

    private AccessState loadAccessState(String normalizedEmail) {
        boolean superAdmin = adminKeyProperties.isConfiguredSuperAdminEmail(normalizedEmail);
        boolean configuredAdmin = adminKeyProperties.isConfiguredAdminEmail(normalizedEmail);
        boolean configuredAllowed = adminKeyProperties.isConfiguredAllowedEmail(normalizedEmail);

        ManagedAdminEmail managedAdminEmail = managedAdminEmailRepository
            .findByNormalizedEmail(normalizedEmail)
            .orElse(null);
        AllowedUserEmail allowedUserEmail = allowedUserEmailRepository
            .findByNormalizedEmail(normalizedEmail)
            .orElse(null);
        UserRacePreference userRacePreference = userRacePreferenceRepository
            .findByNormalizedEmail(normalizedEmail)
            .orElse(null);

        boolean admin = superAdmin || configuredAdmin || managedAdminEmail != null;
        boolean allowed = admin || configuredAllowed || allowedUserEmail != null;
        String nickname = managedAdminEmail != null
            ? normalizeNickname(managedAdminEmail.getNickname())
            : allowedUserEmail != null
                ? normalizeNickname(allowedUserEmail.getNickname())
                : null;
        if (nickname != null && nickname.isBlank()) {
            nickname = null;
        }
        String preferredRace = userRacePreference == null ? null : userRacePreference.getPreferredRace();

        return new AccessState(superAdmin, admin, allowed, nickname, preferredRace);
    }

    private void invalidateAccessState(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return;
        }
        accessStateCache.remove(normalizedEmail);
    }

    private void validateEmail(String email) {
        if (email.isEmpty() || !email.contains("@")) {
            throw new IllegalArgumentException("A valid email is required");
        }
    }

    private void validateNickname(String nickname) {
        if (nickname.isEmpty()) {
            throw new IllegalArgumentException("Nickname is required");
        }
        if (nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new IllegalArgumentException("Nickname must be 100 characters or fewer");
        }
    }

    private void upsertManagedAdminEmail(String actorEmail, String targetEmail, String targetNickname) {
        ManagedAdminEmail managedAdminEmail = managedAdminEmailRepository
            .findByNormalizedEmail(targetEmail)
            .orElseGet(ManagedAdminEmail::new);
        managedAdminEmail.setEmail(targetEmail);
        managedAdminEmail.setNickname(targetNickname);
        if (managedAdminEmail.getCreatedByEmail() == null || managedAdminEmail.getCreatedByEmail().isBlank()) {
            managedAdminEmail.setCreatedByEmail(actorEmail);
        }
        managedAdminEmailRepository.save(managedAdminEmail);
    }

    private void upsertAllowedUserEmail(String actorEmail, String targetEmail, String targetNickname) {
        AllowedUserEmail allowedUserEmail = allowedUserEmailRepository
            .findByNormalizedEmail(targetEmail)
            .orElseGet(AllowedUserEmail::new);
        allowedUserEmail.setEmail(targetEmail);
        allowedUserEmail.setNickname(targetNickname);
        if (allowedUserEmail.getCreatedByEmail() == null || allowedUserEmail.getCreatedByEmail().isBlank()) {
            allowedUserEmail.setCreatedByEmail(actorEmail);
        }
        allowedUserEmailRepository.save(allowedUserEmail);
    }

    private String resolveNickname(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            return null;
        }

        return managedAdminEmailRepository.findByNormalizedEmail(normalizedEmail)
            .map(ManagedAdminEmail::getNickname)
            .map(this::normalizeNickname)
            .filter(nickname -> !nickname.isEmpty())
            .or(() -> allowedUserEmailRepository.findByNormalizedEmail(normalizedEmail)
                .map(AllowedUserEmail::getNickname)
                .map(this::normalizeNickname)
                .filter(nickname -> !nickname.isEmpty()))
            .orElse(null);
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNickname(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeRace(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record AccessState(
        boolean superAdmin,
        boolean admin,
        boolean allowed,
        String nickname,
        String preferredRace
    ) {
    }

    private record CachedAccessState(
        AccessState accessState,
        long expiresAtEpochMs
    ) {
    }

    public record AccessProfile(
        String email,
        String nickname,
        String role,
        boolean admin,
        boolean superAdmin,
        boolean allowed,
        String preferredRace
    ) {
    }

    public record AccessEmailEntry(
        String email,
        String nickname
    ) {
    }

    public record AdminEmailSnapshot(
        List<AccessEmailEntry> superAdmins,
        List<AccessEmailEntry> admins
    ) {
    }

    public record AllowedEmailSnapshot(
        List<AccessEmailEntry> allowedUsers
    ) {
    }
}
