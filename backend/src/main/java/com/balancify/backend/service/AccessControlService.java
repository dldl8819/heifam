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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessControlService {

    private final AdminKeyProperties adminKeyProperties;
    private final ManagedAdminEmailRepository managedAdminEmailRepository;
    private final AllowedUserEmailRepository allowedUserEmailRepository;
    private final UserRacePreferenceRepository userRacePreferenceRepository;

    private static final Set<String> ALLOWED_RACES = Set.of("P", "T", "Z", "PT", "PZ", "TZ", "R");
    private static final int MAX_NICKNAME_LENGTH = 100;

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

        boolean superAdmin = isSuperAdminEmail(normalizedEmail);
        boolean admin = superAdmin || isAdminEmail(normalizedEmail);
        boolean allowed = admin || isServiceAccessAllowed(normalizedEmail);
        String role = superAdmin ? "SUPER_ADMIN" : admin ? "ADMIN" : allowed ? "MEMBER" : "BLOCKED";
        String nickname = resolveNickname(normalizedEmail);
        String preferredRace = userRacePreferenceRepository
            .findByNormalizedEmail(normalizedEmail)
            .map(UserRacePreference::getPreferredRace)
            .orElse(null);

        return new AccessProfile(normalizedEmail, nickname, role, admin, superAdmin, allowed, preferredRace);
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
        if (normalizedEmail.isEmpty()) {
            return false;
        }
        if (adminKeyProperties.isAllowedAdminEmail(normalizedEmail)) {
            return true;
        }
        return managedAdminEmailRepository.existsByNormalizedEmail(normalizedEmail);
    }

    @Transactional(readOnly = true)
    public boolean isServiceAccessAllowed(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            return false;
        }
        if (isAdminEmail(normalizedEmail)) {
            return true;
        }
        if (adminKeyProperties.isConfiguredAllowedEmail(normalizedEmail)) {
            return true;
        }
        return allowedUserEmailRepository.existsByNormalizedEmail(normalizedEmail);
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

        return getAllowedEmailSnapshot();
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
