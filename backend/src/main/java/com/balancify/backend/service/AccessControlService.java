package com.balancify.backend.service;

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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessControlService {

    private static final String ACCESS_STATE_CACHE_TTL_PROPERTY =
        "${balancify.access.state-cache-ttl-ms:30000}";

    public static final String ACTOR_ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ACTOR_ROLE_ADMIN = "ADMIN";
    public static final String ACTOR_ROLE_RESULT_EDITOR = "RESULT_EDITOR";
    public static final String ACTOR_ROLE_MEMBER = "MEMBER";

    private final AdminKeyProperties adminKeyProperties;
    private final ManagedAdminEmailRepository managedAdminEmailRepository;
    private final AdminMmrAccessEmailRepository adminMmrAccessEmailRepository;
    private final AllowedUserEmailRepository allowedUserEmailRepository;
    private final UserRacePreferenceRepository userRacePreferenceRepository;
    private final MatchResultEditorEmailRepository matchResultEditorEmailRepository;
    private final ConcurrentMap<String, CachedAccessState> accessStateCache = new ConcurrentHashMap<>();

    /**
     * How long a resolved access state stays cached in-process.
     *
     * <p>Every authenticated request resolves the caller's access state, and each resolution costs
     * five single-row lookups (managed admins, MMR access, allowed users, race preference, match
     * result editors). Those
     * tables hold a handful of rows and only change when an admin edits access, so re-reading them
     * on each request was the largest source of query volume on the database.
     *
     * <p>Grants and revocations evict the entry immediately on the instance that applied them, so
     * this TTL only bounds how long another instance can keep serving a stale decision. Set
     * {@code balancify.access.state-cache-ttl-ms} to 0 to disable caching entirely.
     */
    private final long accessStateCacheTtlMs;

    private static final int MAX_NICKNAME_LENGTH = 100;

    @Autowired
    public AccessControlService(
        AdminKeyProperties adminKeyProperties,
        ManagedAdminEmailRepository managedAdminEmailRepository,
        AdminMmrAccessEmailRepository adminMmrAccessEmailRepository,
        AllowedUserEmailRepository allowedUserEmailRepository,
        UserRacePreferenceRepository userRacePreferenceRepository,
        MatchResultEditorEmailRepository matchResultEditorEmailRepository,
        @Value(ACCESS_STATE_CACHE_TTL_PROPERTY) long accessStateCacheTtlMs
    ) {
        this.adminKeyProperties = adminKeyProperties;
        this.managedAdminEmailRepository = managedAdminEmailRepository;
        this.adminMmrAccessEmailRepository = adminMmrAccessEmailRepository;
        this.allowedUserEmailRepository = allowedUserEmailRepository;
        this.userRacePreferenceRepository = userRacePreferenceRepository;
        this.matchResultEditorEmailRepository = matchResultEditorEmailRepository;
        this.accessStateCacheTtlMs = Math.max(0L, accessStateCacheTtlMs);
    }

    @Transactional(readOnly = true)
    public AccessProfile resolveAccessProfile(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            return new AccessProfile("", null, "BLOCKED", false, false, false, false, null);
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
            accessState.canViewMmr(),
            accessState.preferredRace(),
            accessState.matchResultEditor()
        );
    }

    @Transactional(readOnly = true)
    public boolean isMatchResultEditor(String email) {
        String normalizedEmail = normalizeEmail(email);
        return !normalizedEmail.isEmpty() && resolveAccessState(normalizedEmail).matchResultEditor();
    }

    // Stamped on audit logs, so who may later read a log follows the actor's role at the time.
    @Transactional(readOnly = true)
    public String resolveActorRole(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            return null;
        }
        AccessState accessState = resolveAccessState(normalizedEmail);
        if (accessState.superAdmin()) {
            return ACTOR_ROLE_SUPER_ADMIN;
        }
        if (accessState.admin()) {
            return ACTOR_ROLE_ADMIN;
        }
        if (accessState.matchResultEditor()) {
            return ACTOR_ROLE_RESULT_EDITOR;
        }
        return accessState.allowed() ? ACTOR_ROLE_MEMBER : null;
    }

    @Transactional
    public AccessProfile upsertPreferredRace(String email, String race) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            throw new IllegalArgumentException("A valid email is required");
        }

        String normalizedRace = normalizeRace(race);
        if (normalizedRace.isEmpty()) {
            throw new IllegalArgumentException("Invalid race. Allowed values: P, T, Z, PT, PZ, TZ, PTZ");
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
    public boolean canViewMmr(String email) {
        String normalizedEmail = normalizeEmail(email);
        return !normalizedEmail.isEmpty() && resolveAccessState(normalizedEmail).canViewMmr();
    }

    @Transactional(readOnly = true)
    public boolean isServiceAccessAllowed(String email) {
        String normalizedEmail = normalizeEmail(email);
        return !normalizedEmail.isEmpty() && resolveAccessState(normalizedEmail).allowed();
    }

    public boolean hasConfiguredAccessGrant(String email) {
        String normalizedEmail = normalizeEmail(email);
        return !normalizedEmail.isEmpty()
            && (adminKeyProperties.isConfiguredSuperAdminEmail(normalizedEmail)
                || adminKeyProperties.isConfiguredAdminEmail(normalizedEmail)
                || adminKeyProperties.isConfiguredAllowedEmail(normalizedEmail));
    }

    @Transactional(readOnly = true)
    public AdminEmailSnapshot getAdminEmailSnapshot() {
        List<String> superAdminEmails = adminKeyProperties
            .getNormalizedSuperAdminEmails()
            .stream()
            .sorted()
            .toList();

        List<AccessEmailEntry> superAdmins = superAdminEmails.stream()
            .map(superAdminEmail -> new AccessEmailEntry(superAdminEmail, resolveNickname(superAdminEmail), true))
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
            .map(adminEmail -> new AccessEmailEntry(adminEmail, resolveNickname(adminEmail), canViewMmr(adminEmail)))
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
            .map(allowedEmail -> new AccessEmailEntry(allowedEmail, resolveNickname(allowedEmail), false))
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
        removeMatchResultEditorRow(normalizedTargetEmail);
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
        adminMmrAccessEmailRepository
            .findByNormalizedEmail(normalizedTargetEmail)
            .ifPresent(adminMmrAccessEmailRepository::delete);
        invalidateAccessState(normalizedTargetEmail);

        return getAdminEmailSnapshot();
    }

    @Transactional
    public AdminEmailSnapshot updateManagedAdminMmrAccess(
        String actorEmail,
        String targetEmail,
        boolean canViewMmr
    ) {
        String normalizedActorEmail = normalizeEmail(actorEmail);
        String normalizedTargetEmail = normalizeEmail(targetEmail);
        validateEmail(normalizedTargetEmail);

        if (!isSuperAdminEmail(normalizedActorEmail)) {
            throw new IllegalArgumentException("Only super admins can update operator MMR access");
        }
        if (adminKeyProperties.isConfiguredSuperAdminEmail(normalizedTargetEmail)) {
            throw new IllegalArgumentException("Super admins always have MMR access");
        }
        if (!isAdminEmail(normalizedTargetEmail)) {
            throw new IllegalArgumentException("Target email is not an operator");
        }

        if (canViewMmr) {
            AdminMmrAccessEmail adminMmrAccessEmail = adminMmrAccessEmailRepository
                .findByNormalizedEmail(normalizedTargetEmail)
                .orElseGet(AdminMmrAccessEmail::new);
            adminMmrAccessEmail.setEmail(normalizedTargetEmail);
            if (adminMmrAccessEmail.getCreatedByEmail() == null || adminMmrAccessEmail.getCreatedByEmail().isBlank()) {
                adminMmrAccessEmail.setCreatedByEmail(normalizedActorEmail);
            }
            adminMmrAccessEmailRepository.save(adminMmrAccessEmail);
        } else {
            adminMmrAccessEmailRepository
                .findByNormalizedEmail(normalizedTargetEmail)
                .ifPresent(adminMmrAccessEmailRepository::delete);
        }
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
        removeMatchResultEditorRow(normalizedTargetEmail);
        invalidateAccessState(normalizedTargetEmail);

        return getAllowedEmailSnapshot();
    }

    @Transactional(readOnly = true)
    public List<AccessEmailEntry> getMatchResultEditors() {
        return matchResultEditorEmailRepository.findAllByOrderByNormalizedEmailAsc()
            .stream()
            .map(MatchResultEditorEmail::getNormalizedEmail)
            .map(editorEmail -> new AccessEmailEntry(editorEmail, resolveNickname(editorEmail), false))
            .toList();
    }

    @Transactional
    public List<AccessEmailEntry> addMatchResultEditor(String actorEmail, String targetEmail) {
        String normalizedActorEmail = normalizeEmail(actorEmail);
        String normalizedTargetEmail = normalizeEmail(targetEmail);
        validateEmail(normalizedTargetEmail);

        if (!isSuperAdminEmail(normalizedActorEmail)) {
            throw new IllegalArgumentException("Only super admins can grant match result editing");
        }
        if (isAdminEmail(normalizedTargetEmail)) {
            throw new IllegalArgumentException("Operators can already edit match results");
        }
        if (!isServiceAccessAllowed(normalizedTargetEmail)) {
            throw new IllegalArgumentException("Target email is not a registered member");
        }

        MatchResultEditorEmail editor = matchResultEditorEmailRepository
            .findByNormalizedEmail(normalizedTargetEmail)
            .orElseGet(MatchResultEditorEmail::new);
        editor.setEmail(normalizedTargetEmail);
        if (editor.getCreatedByEmail() == null || editor.getCreatedByEmail().isBlank()) {
            editor.setCreatedByEmail(normalizedActorEmail);
        }
        matchResultEditorEmailRepository.save(editor);
        invalidateAccessState(normalizedTargetEmail);

        return getMatchResultEditors();
    }

    @Transactional
    public List<AccessEmailEntry> removeMatchResultEditor(String actorEmail, String targetEmail) {
        String normalizedActorEmail = normalizeEmail(actorEmail);
        String normalizedTargetEmail = normalizeEmail(targetEmail);
        validateEmail(normalizedTargetEmail);

        if (!isSuperAdminEmail(normalizedActorEmail)) {
            throw new IllegalArgumentException("Only super admins can revoke match result editing");
        }
        removeMatchResultEditorRow(normalizedTargetEmail);
        invalidateAccessState(normalizedTargetEmail);

        return getMatchResultEditors();
    }

    private void removeMatchResultEditorRow(String normalizedEmail) {
        matchResultEditorEmailRepository
            .findByNormalizedEmail(normalizedEmail)
            .ifPresent(matchResultEditorEmailRepository::delete);
    }

    @Transactional
    public AllowedEmailSnapshot updateAllowedUserEmailNickname(
        String actorEmail,
        String targetEmail,
        String targetNickname
    ) {
        String normalizedActorEmail = normalizeEmail(actorEmail);
        String normalizedTargetEmail = normalizeEmail(targetEmail);
        String normalizedTargetNickname = normalizeNickname(targetNickname);
        validateEmail(normalizedTargetEmail);
        validateNickname(normalizedTargetNickname);

        if (!isAdminEmail(normalizedActorEmail)) {
            throw new IllegalArgumentException("Only admins can update allowed member nicknames");
        }
        if (isAdminEmail(normalizedTargetEmail)) {
            throw new IllegalArgumentException("Admin email nickname cannot be updated here");
        }

        AllowedUserEmail allowedUserEmail = allowedUserEmailRepository
            .findByNormalizedEmail(normalizedTargetEmail)
            .orElseThrow(() -> new IllegalArgumentException("Target email is not registered"));
        allowedUserEmail.setNickname(normalizedTargetNickname);
        allowedUserEmailRepository.save(allowedUserEmail);
        invalidateAccessState(normalizedTargetEmail);

        return getAllowedEmailSnapshot();
    }

    private AccessState resolveAccessState(String normalizedEmail) {
        if (accessStateCacheTtlMs <= 0L) {
            return loadAccessState(normalizedEmail);
        }
        long now = System.currentTimeMillis();
        CachedAccessState cached = accessStateCache.get(normalizedEmail);
        if (cached != null && cached.expiresAtEpochMs() > now) {
            return cached.accessState();
        }

        AccessState computed = loadAccessState(normalizedEmail);
        accessStateCache.put(
            normalizedEmail,
            new CachedAccessState(computed, now + accessStateCacheTtlMs)
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
        AdminMmrAccessEmail adminMmrAccessEmail = adminMmrAccessEmailRepository
            .findByNormalizedEmail(normalizedEmail)
            .orElse(null);
        AllowedUserEmail allowedUserEmail = allowedUserEmailRepository
            .findByNormalizedEmail(normalizedEmail)
            .orElse(null);
        UserRacePreference userRacePreference = userRacePreferenceRepository
            .findByNormalizedEmail(normalizedEmail)
            .orElse(null);
        boolean hasMatchResultEditorRow = matchResultEditorEmailRepository
            .findByNormalizedEmail(normalizedEmail)
            .isPresent();

        boolean admin = superAdmin || configuredAdmin || managedAdminEmail != null;
        boolean allowed = admin || configuredAllowed || allowedUserEmail != null;
        boolean canViewMmr = superAdmin || (admin && adminMmrAccessEmail != null);
        boolean matchResultEditor = !admin && allowed && hasMatchResultEditorRow;
        String nickname = managedAdminEmail != null
            ? normalizeNickname(managedAdminEmail.getNickname())
            : allowedUserEmail != null
                ? normalizeNickname(allowedUserEmail.getNickname())
                : null;
        if (nickname != null && nickname.isBlank()) {
            nickname = null;
        }
        String preferredRace = userRacePreference == null ? null : userRacePreference.getPreferredRace();

        return new AccessState(superAdmin, admin, allowed, canViewMmr, nickname, preferredRace, matchResultEditor);
    }

    public void evictAccountCache(String email) {
        invalidateAccessState(normalizeEmail(email));
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

    /** The nickname shown for an access email, or null when none is set. */
    public String resolveDisplayNickname(String email) {
        return resolveNickname(email);
    }

    /** {@link #resolveDisplayNickname} for many emails with two queries; emails without one map to null. */
    public Map<String, String> resolveDisplayNicknames(Collection<String> emails) {
        Map<String, String> knownNicknames = new HashMap<>();
        for (AllowedUserEmail allowedUser : allowedUserEmailRepository.findAllByOrderByNormalizedEmailAsc()) {
            putNickname(knownNicknames, allowedUser.getNormalizedEmail(), allowedUser.getNickname());
        }
        // An admin entry's nickname wins, as in resolveNickname.
        for (ManagedAdminEmail admin : managedAdminEmailRepository.findAllByOrderByNormalizedEmailAsc()) {
            putNickname(knownNicknames, admin.getNormalizedEmail(), admin.getNickname());
        }

        Map<String, String> nicknames = new HashMap<>();
        for (String email : emails) {
            String normalizedEmail = normalizeEmail(email);
            nicknames.put(normalizedEmail, knownNicknames.get(normalizedEmail));
        }
        return nicknames;
    }

    private void putNickname(Map<String, String> nicknames, String email, String nickname) {
        String normalizedNickname = normalizeNickname(nickname);
        if (!normalizedNickname.isEmpty()) {
            nicknames.put(normalizeEmail(email), normalizedNickname);
        }
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
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return PlayerRacePolicy.normalizeCapability(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private record AccessState(
        boolean superAdmin,
        boolean admin,
        boolean allowed,
        boolean canViewMmr,
        String nickname,
        String preferredRace,
        boolean matchResultEditor
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
        boolean canViewMmr,
        String preferredRace,
        boolean matchResultEditor
    ) {
        public AccessProfile(
            String email,
            String nickname,
            String role,
            boolean admin,
            boolean superAdmin,
            boolean allowed,
            boolean canViewMmr,
            String preferredRace
        ) {
            this(email, nickname, role, admin, superAdmin, allowed, canViewMmr, preferredRace, false);
        }
    }

    public record AccessEmailEntry(
        String email,
        String nickname,
        boolean canViewMmr
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
