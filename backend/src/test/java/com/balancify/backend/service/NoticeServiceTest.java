package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.NoticeResponse;
import com.balancify.backend.domain.Notice;
import com.balancify.backend.repository.NoticeRepository;
import java.util.List;
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
class NoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private AccessControlService accessControlService;

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeService(noticeRepository, accessControlService);
    }

    private Notice notice(Long id, String title, String authorEmail) {
        Notice notice = new Notice();
        notice.setId(id);
        notice.setGroupId(1L);
        notice.setTitle(title);
        notice.setContent("content-" + id);
        notice.setAuthorEmail(authorEmail);
        return notice;
    }

    @Test
    void resolvesAuthorNicknameForEachDistinctEmailOnce() {
        when(noticeRepository.findByGroupIdOrderByCreatedAtDescIdDesc(1L))
            .thenReturn(List.of(notice(1L, "first", "ops@hei.gg"), notice(2L, "second", "ops@hei.gg")));
        when(accessControlService.resolveAccessProfile("ops@hei.gg"))
            .thenReturn(new AccessControlService.AccessProfile(
                "ops@hei.gg", "OpsUser", "ADMIN", true, false, true, true, null
            ));

        List<NoticeResponse> responses = noticeService.getNotices(1L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).authorNickname()).isEqualTo("OpsUser");
        assertThat(responses.get(1).authorNickname()).isEqualTo("OpsUser");
        org.mockito.Mockito.verify(accessControlService, org.mockito.Mockito.times(1))
            .resolveAccessProfile("ops@hei.gg");
    }

    @Test
    void returnsNoticeDetailWhenFound() {
        when(noticeRepository.findByIdAndGroupId(5L, 1L)).thenReturn(Optional.of(notice(5L, "detail", "ops@hei.gg")));
        when(accessControlService.resolveAccessProfile("ops@hei.gg"))
            .thenReturn(new AccessControlService.AccessProfile(
                "ops@hei.gg", "OpsUser", "ADMIN", true, false, true, true, null
            ));

        NoticeResponse response = noticeService.getNotice(1L, 5L);

        assertThat(response.title()).isEqualTo("detail");
        assertThat(response.authorNickname()).isEqualTo("OpsUser");
    }

    @Test
    void throwsWhenNoticeIsMissing() {
        when(noticeRepository.findByIdAndGroupId(404L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.getNotice(1L, 404L))
            .isInstanceOf(java.util.NoSuchElementException.class)
            .hasMessage("Notice not found");
    }
}
