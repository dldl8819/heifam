package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.NicknameRequestListResponse;
import com.balancify.backend.api.group.dto.NicknameRequestResponse;
import com.balancify.backend.repository.NicknameRequestRepository;
import com.balancify.backend.repository.NicknameRequestRepository.RequestRow;
import com.balancify.backend.service.exception.BoardLimitException;
import com.balancify.backend.service.exception.NicknameRequestConflictException;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where members ask for a nickname change. A request is a ticket: a member files one (one waiting
 * at a time) and may call it off; an admin marks it APPROVED or REJECTED with an optional note.
 * Nothing here changes a nickname: the admin does that by hand where nicknames are kept, and
 * marks the request afterwards.
 */
@Service
public class NicknameRequestService {

    static final int MAX_NICKNAME_LENGTH = 50;
    static final int MAX_REASON_LENGTH = 300;
    static final int MAX_NOTE_LENGTH = 300;
    // Requests one person may file within a day, those called off included.
    static final int REQUESTS_PER_DAY = 3;
    static final int OWN_LIST_LIMIT = 30;
    static final int ADMIN_LIST_LIMIT = 200;

    private final NicknameRequestRepository nicknameRequestRepository;
    private final AccessControlService accessControlService;

    public NicknameRequestService(
        NicknameRequestRepository nicknameRequestRepository,
        AccessControlService accessControlService
    ) {
        this.nicknameRequestRepository = nicknameRequestRepository;
        this.accessControlService = accessControlService;
    }

    /** The reader's own requests, and for admins everyone's that an admin has decided or has yet to. */
    @Transactional(readOnly = true)
    public NicknameRequestListResponse list(Long groupId, String email) {
        String reader = normalizeEmail(email);
        boolean admin = accessControlService.isAdminEmail(reader);
        List<RequestRow> mine = nicknameRequestRepository.listByRequester(groupId, reader, OWN_LIST_LIMIT);
        List<RequestRow> received = admin
            ? nicknameRequestRepository.listForAdmins(groupId, ADMIN_LIST_LIMIT)
            : List.of();

        Set<String> deciders = new LinkedHashSet<>();
        mine.forEach(row -> addEmail(deciders, row.processedByEmail()));
        received.forEach(row -> addEmail(deciders, row.processedByEmail()));
        Map<String, String> nicknames = deciders.isEmpty()
            ? Map.of()
            : accessControlService.resolveDisplayNicknames(deciders);

        return new NicknameRequestListResponse(
            mine.stream().map(row -> response(row, reader, admin, nicknames)).toList(),
            received.stream().map(row -> response(row, reader, admin, nicknames)).toList(),
            admin,
            accessControlService.resolveDisplayNickname(reader)
        );
    }

    @Transactional
    public NicknameRequestListResponse create(Long groupId, String email, String desiredNickname, String reason) {
        String requester = normalizeEmail(email);
        String desired = desiredNickname == null ? "" : desiredNickname.trim();
        if (desired.isEmpty() || desired.length() > MAX_NICKNAME_LENGTH) {
            throw new IllegalArgumentException("바꿀 닉네임은 1~" + MAX_NICKNAME_LENGTH + "자로 입력해 주세요.");
        }
        String cleanReason = optionalText(reason, MAX_REASON_LENGTH, "사유는 " + MAX_REASON_LENGTH + "자 이하로 입력해 주세요.");
        String current = accessControlService.resolveDisplayNickname(requester);
        if (current != null && current.trim().equals(desired)) {
            throw new IllegalArgumentException("지금 닉네임과 같습니다.");
        }
        if (nicknameRequestRepository.countSince(groupId, requester, OffsetDateTime.now().minusDays(1)) >= REQUESTS_PER_DAY) {
            throw new BoardLimitException("하루에 신청할 수 있는 횟수를 넘었습니다. 내일 다시 신청해 주세요.");
        }
        try {
            nicknameRequestRepository.insert(groupId, requester, current == null ? null : current.trim(), desired, cleanReason);
        } catch (DuplicateKeyException exception) {
            throw new NicknameRequestConflictException("이미 대기 중인 신청이 있습니다.");
        }
        return list(groupId, requester);
    }

    /** Only whoever filed a request calls it off, and only while it waits. Another person's reads as missing. */
    @Transactional
    public NicknameRequestListResponse cancel(Long groupId, Long requestId, String email) {
        String actor = normalizeEmail(email);
        RequestRow request = nicknameRequestRepository.find(groupId, requestId)
            .filter(row -> actor.equals(normalizeEmail(row.requesterEmail())))
            .orElseThrow(() -> new NoSuchElementException("Request not found"));
        if (!nicknameRequestRepository.cancel(request.id())) {
            throw new NicknameRequestConflictException("이미 처리된 신청입니다.");
        }
        return list(groupId, actor);
    }

    /** An admin's decision on a waiting request. It records the decision and changes no nickname. */
    @Transactional
    public NicknameRequestListResponse decide(Long groupId, Long requestId, String email, String status, String note) {
        String actor = normalizeEmail(email);
        if (!accessControlService.isAdminEmail(actor)) {
            throw new NoSuchElementException("Request not found");
        }
        String decision = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!NicknameRequestRepository.STATUS_APPROVED.equals(decision)
            && !NicknameRequestRepository.STATUS_REJECTED.equals(decision)) {
            throw new IllegalArgumentException("처리 결과는 승인 또는 반려여야 합니다.");
        }
        String cleanNote = optionalText(note, MAX_NOTE_LENGTH, "메모는 " + MAX_NOTE_LENGTH + "자 이하로 입력해 주세요.");
        RequestRow request = nicknameRequestRepository.find(groupId, requestId)
            .filter(row -> !NicknameRequestRepository.STATUS_CANCELED.equals(row.status()))
            .orElseThrow(() -> new NoSuchElementException("Request not found"));
        if (!nicknameRequestRepository.decide(request.id(), decision, cleanNote, actor)) {
            throw new NicknameRequestConflictException("이미 처리된 신청입니다.");
        }
        return list(groupId, actor);
    }

    private NicknameRequestResponse response(RequestRow row, String reader, boolean admin, Map<String, String> nicknames) {
        boolean mine = reader.equals(normalizeEmail(row.requesterEmail()));
        boolean pending = NicknameRequestRepository.STATUS_PENDING.equals(row.status());
        return new NicknameRequestResponse(
            row.id(),
            row.currentNickname(),
            row.desiredNickname(),
            row.reason(),
            row.status(),
            row.adminNote(),
            nicknames.get(normalizeEmail(row.processedByEmail())),
            row.processedAt(),
            row.createdAt(),
            mine,
            mine && pending,
            admin && pending
        );
    }

    private static void addEmail(Set<String> emails, String email) {
        String normalized = normalizeEmail(email);
        if (!normalized.isEmpty()) {
            emails.add(normalized);
        }
    }

    /** Null when left empty. */
    private static String optionalText(String value, int maxLength, String message) {
        String text = value == null ? "" : value.trim();
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return text.isEmpty() ? null : text;
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
