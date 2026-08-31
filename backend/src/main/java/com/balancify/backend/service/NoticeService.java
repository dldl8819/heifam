package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.NoticeResponse;
import com.balancify.backend.domain.Notice;
import com.balancify.backend.repository.NoticeRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final AccessControlService accessControlService;

    public NoticeService(NoticeRepository noticeRepository, AccessControlService accessControlService) {
        this.noticeRepository = noticeRepository;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public List<NoticeResponse> getNotices(Long groupId) {
        List<Notice> notices = noticeRepository.findByGroupIdOrderByCreatedAtDescIdDesc(groupId);
        Map<String, String> nicknameByEmail = loadAuthorNicknamesByEmail(notices);
        return notices.stream()
            .map(notice -> toResponse(notice, nicknameByEmail))
            .toList();
    }

    @Transactional(readOnly = true)
    public NoticeResponse getNotice(Long groupId, Long noticeId) {
        Notice notice = noticeRepository.findByIdAndGroupId(noticeId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Notice not found"));
        Map<String, String> nicknameByEmail = loadAuthorNicknamesByEmail(List.of(notice));
        return toResponse(notice, nicknameByEmail);
    }

    private Map<String, String> loadAuthorNicknamesByEmail(List<Notice> notices) {
        Set<String> emails = new LinkedHashSet<>();
        for (Notice notice : notices) {
            String email = safeTrim(notice.getAuthorEmail()).toLowerCase(Locale.ROOT);
            if (!email.isEmpty()) {
                emails.add(email);
            }
        }

        Map<String, String> nicknameByEmail = new LinkedHashMap<>();
        for (String email : emails) {
            String nickname = safeTrim(accessControlService.resolveAccessProfile(email).nickname());
            if (!nickname.isEmpty()) {
                nicknameByEmail.put(email, nickname);
            }
        }
        return nicknameByEmail;
    }

    private NoticeResponse toResponse(Notice notice, Map<String, String> nicknameByEmail) {
        String email = safeTrim(notice.getAuthorEmail()).toLowerCase(Locale.ROOT);
        return new NoticeResponse(
            notice.getId(),
            notice.getTitle(),
            notice.getContent(),
            nicknameByEmail.get(email),
            notice.getCreatedAt(),
            notice.getUpdatedAt()
        );
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
