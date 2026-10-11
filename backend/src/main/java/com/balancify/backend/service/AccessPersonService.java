package com.balancify.backend.service;

import com.balancify.backend.repository.AccessPersonMergeRepository;
import com.balancify.backend.repository.AccessPersonRepository;
import com.balancify.backend.repository.AccessPersonRepository.PersonEmailRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emails grouped as one person, by a super admin: a member allowed as 민식, 민식(2) and 민식(3)
 * becomes one 민식. A notice vote then counts once a person, and posts, comments, voter lists and
 * the operation log show the person's name. Access stays with each email; grouping grants nothing.
 * Groups are only ever made by hand: nicknames that differ by a trailing "(number)" are offered
 * as suggestions, never grouped on their own.
 */
@Service
public class AccessPersonService {

    static final int DISPLAY_NAME_MAX_LENGTH = 100;
    private static final Pattern NUMBERED_NICKNAME = Pattern.compile("^(.*?)\\s*\\(\\s*\\d+\\s*\\)$");

    public record Member(String email, String nickname, boolean hasAccess) {
    }

    public record Person(Long id, String displayName, List<Member> members) {
    }

    /** Emails whose nicknames are one name with or without a "(number)": to group as one, or to add to personId. */
    public record Suggestion(String displayName, Long personId, List<Member> members) {
    }

    /** Every email with access now, and the person it is with, if any. */
    public record Candidate(String email, String nickname, Long personId) {
    }

    public record Overview(List<Person> people, List<Suggestion> suggestions, List<Candidate> candidates) {
    }

    private final AccessPersonRepository accessPersonRepository;
    private final AccessPersonMergeRepository accessPersonMergeRepository;
    private final AccessControlService accessControlService;
    private final OperationAuditLogService operationAuditLogService;

    public AccessPersonService(
        AccessPersonRepository accessPersonRepository,
        AccessPersonMergeRepository accessPersonMergeRepository,
        AccessControlService accessControlService,
        OperationAuditLogService operationAuditLogService
    ) {
        this.accessPersonRepository = accessPersonRepository;
        this.accessPersonMergeRepository = accessPersonMergeRepository;
        this.accessControlService = accessControlService;
        this.operationAuditLogService = operationAuditLogService;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        Map<String, String> access = accessNicknames();
        List<PersonEmailRow> rows = accessPersonRepository.listAll();

        Map<Long, Person> people = new LinkedHashMap<>();
        Map<String, Long> personOf = new LinkedHashMap<>();
        for (PersonEmailRow row : rows) {
            personOf.put(row.email(), row.personId());
            people.computeIfAbsent(row.personId(), id -> new Person(id, row.displayName(), new ArrayList<>()))
                .members()
                .add(new Member(row.email(), access.get(row.email()), access.containsKey(row.email())));
        }

        List<Candidate> candidates = access.entrySet().stream()
            .map(entry -> new Candidate(entry.getKey(), entry.getValue(), personOf.get(entry.getKey())))
            .sorted(Comparator.comparing((Candidate candidate) -> sortKey(candidate.nickname(), candidate.email())))
            .toList();

        return new Overview(List.copyOf(people.values()), suggestions(access, personOf, people), candidates);
    }

    /**
     * Groups these emails as one person. Emails already with a person bring that person along: the
     * group with the lowest id takes everyone and the name given, the others go. What the emails
     * did before is then made one person's: one like, read, vote and prediction each, and one
     * point account, with points earned twice for the same thing given back (AccessPersonMergeRepository).
     */
    @Transactional
    public Overview group(List<String> emails, String displayName, String actorEmail, String actorNickname) {
        Map<String, String> access = accessNicknames();
        Set<String> chosen = new LinkedHashSet<>();
        for (String email : emails == null ? List.<String>of() : emails) {
            String normalized = normalizeEmail(email);
            if (!normalized.isEmpty()) {
                chosen.add(normalized);
            }
        }
        if (chosen.size() < 2) {
            throw new IllegalArgumentException("같은 사람으로 묶을 이메일을 두 개 이상 골라 주세요.");
        }
        for (String email : chosen) {
            if (!access.containsKey(email)) {
                throw new IllegalArgumentException("접속 권한이 없는 이메일은 묶을 수 없습니다.");
            }
        }

        Map<String, Long> existing = accessPersonRepository.personIdsOf(chosen);
        TreeSet<Long> personIds = new TreeSet<>(existing.values());
        accessPersonRepository.lockPeople(personIds);

        String name = normalizeDisplayName(displayName);
        if (name.isEmpty()) {
            name = personIds.isEmpty()
                ? defaultName(chosen, access)
                : accessPersonRepository.displayNameOf(personIds.first()).orElse(defaultName(chosen, access));
        }
        long personId = personIds.isEmpty()
            ? accessPersonRepository.createPerson(name, normalizeEmail(actorEmail))
            : personIds.first();
        accessPersonRepository.attach(personId, chosen);
        for (Long other : personIds) {
            if (other != personId) {
                accessPersonRepository.attach(personId, emailsOf(other));
                accessPersonRepository.deletePerson(other);
            }
        }
        accessPersonRepository.rename(personId, name);
        AccessPersonMergeRepository.MergeOutcome merged = accessPersonMergeRepository.merge(personId);

        operationAuditLogService.recordAccessPeople(
            OperationAuditLogService.ACTION_ACCESS_PEOPLE_GROUPED,
            actorEmail,
            actorNickname,
            personId,
            name,
            "emails=" + accessPersonRepository.countEmails(personId)
                + ", duplicatesTidied=" + merged.duplicatesTidied()
                + ", predictionsTidied=" + merged.predictionsTidied()
                + ", pointsGivenBack=" + merged.pointsGivenBack()
        );
        return overview();
    }

    @Transactional
    public Overview rename(Long personId, String displayName, String actorEmail, String actorNickname) {
        String name = normalizeDisplayName(displayName);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("대표 이름을 입력해 주세요.");
        }
        String previous = accessPersonRepository.displayNameOf(personId)
            .orElseThrow(() -> new NoSuchElementException("Person not found"));
        if (!previous.equals(name)) {
            accessPersonRepository.rename(personId, name);
            operationAuditLogService.recordAccessPeople(
                OperationAuditLogService.ACTION_ACCESS_PEOPLE_RENAMED, actorEmail, actorNickname, personId, name, null
            );
        }
        return overview();
    }

    /** Takes one email out of its person; a person left with a single email is no longer one. */
    @Transactional
    public Overview ungroup(String email, String actorEmail, String actorNickname) {
        String normalized = normalizeEmail(email);
        Long personId = accessPersonRepository.personIdsOf(List.of(normalized)).get(normalized);
        if (personId == null) {
            throw new NoSuchElementException("Email is not grouped");
        }
        accessPersonRepository.lockPeople(List.of(personId));
        String name = accessPersonRepository.displayNameOf(personId).orElse(null);
        accessPersonRepository.detach(normalized);
        if (accessPersonRepository.countEmails(personId) < 2) {
            accessPersonRepository.deletePerson(personId);
        }
        operationAuditLogService.recordAccessPeople(
            OperationAuditLogService.ACTION_ACCESS_PEOPLE_UNGROUPED, actorEmail, actorNickname, personId, name, null
        );
        return overview();
    }

    /** "민식(2)" and "민식 (3)" are named "민식"; any other nickname is its own. */
    static String baseName(String nickname) {
        String trimmed = nickname == null ? "" : nickname.trim();
        Matcher matcher = NUMBERED_NICKNAME.matcher(trimmed);
        return matcher.matches() && !matcher.group(1).isBlank() ? matcher.group(1).trim() : trimmed;
    }

    private List<Suggestion> suggestions(
        Map<String, String> access,
        Map<String, Long> personOf,
        Map<Long, Person> people
    ) {
        Map<String, List<String>> byName = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        access.forEach((email, nickname) -> {
            String base = baseName(nickname);
            if (!base.isEmpty()) {
                String key = base.toLowerCase(Locale.ROOT);
                byName.computeIfAbsent(key, ignored -> new ArrayList<>()).add(email);
                names.putIfAbsent(key, base);
            }
        });

        List<Suggestion> suggestions = new ArrayList<>();
        byName.forEach((key, emails) -> {
            if (emails.size() < 2) {
                return;
            }
            Set<Long> ids = new HashSet<>();
            boolean anyLoose = false;
            for (String email : emails) {
                Long id = personOf.get(email);
                if (id == null) {
                    anyLoose = true;
                } else {
                    ids.add(id);
                }
            }
            if (!anyLoose && ids.size() == 1) {
                return; // already one person
            }
            Long personId = ids.stream().min(Long::compare).orElse(null);
            String displayName = personId == null ? names.get(key) : people.get(personId).displayName();
            List<Member> members = emails.stream()
                .sorted(Comparator.comparing((String email) -> sortKey(access.get(email), email)))
                .map(email -> new Member(email, access.get(email), true))
                .toList();
            suggestions.add(new Suggestion(displayName, personId, members));
        });
        suggestions.sort(Comparator.comparing(Suggestion::displayName));
        return suggestions;
    }

    /** Every email with access now (configured or listed, admins or members), with its nickname or null. */
    private Map<String, String> accessNicknames() {
        Map<String, String> access = new LinkedHashMap<>();
        AccessControlService.AdminEmailSnapshot admins = accessControlService.getAdminEmailSnapshot();
        AccessControlService.AllowedEmailSnapshot allowed = accessControlService.getAllowedEmailSnapshot();
        List<AccessControlService.AccessEmailEntry> entries = new ArrayList<>();
        entries.addAll(admins.superAdmins());
        entries.addAll(admins.admins());
        entries.addAll(allowed.allowedUsers());
        for (AccessControlService.AccessEmailEntry entry : entries) {
            String email = normalizeEmail(entry.email());
            if (email.isEmpty()) {
                continue;
            }
            String nickname = entry.nickname() == null || entry.nickname().isBlank() ? null : entry.nickname().trim();
            if (access.get(email) == null) {
                access.put(email, nickname);
            }
        }
        return access;
    }

    private List<String> emailsOf(Long personId) {
        return accessPersonRepository.listAll().stream()
            .filter(row -> Objects.equals(row.personId(), personId))
            .map(PersonEmailRow::email)
            .toList();
    }

    private String defaultName(Set<String> emails, Map<String, String> access) {
        return emails.stream()
            .map(access::get)
            .filter(Objects::nonNull)
            .map(AccessPersonService::baseName)
            .filter(name -> !name.isEmpty())
            .min(Comparator.comparing(String::length))
            .map(AccessPersonService::normalizeDisplayName)
            .orElseThrow(() -> new IllegalArgumentException("대표 이름을 입력해 주세요."));
    }

    private static String normalizeDisplayName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.codePointCount(0, trimmed.length()) > DISPLAY_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("대표 이름은 " + DISPLAY_NAME_MAX_LENGTH + "자까지 쓸 수 있습니다.");
        }
        return trimmed;
    }

    private static String sortKey(String nickname, String email) {
        return (nickname == null ? "￿" : nickname) + "\u0000" + email;
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
