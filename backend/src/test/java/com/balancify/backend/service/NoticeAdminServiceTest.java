package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.NoticeCreateRequest;
import com.balancify.backend.api.group.dto.NoticeResponse;
import com.balancify.backend.api.group.dto.NoticeUpdateRequest;
import com.balancify.backend.domain.Notice;
import com.balancify.backend.repository.NoticeRepository;
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
class NoticeAdminServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    private NoticeAdminService noticeAdminService;

    @BeforeEach
    void setUp() {
        noticeAdminService = new NoticeAdminService(noticeRepository, accessControlService, operationAuditLogService);
        when(accessControlService.isAdminEmail("ops@hei.gg")).thenReturn(true);
        when(accessControlService.isAdminEmail("member@hei.gg")).thenReturn(false);
        when(noticeRepository.save(any(Notice.class))).thenAnswer(invocation -> {
            Notice notice = invocation.getArgument(0);
            if (notice.getId() == null) {
                notice.setId(1L);
            }
            return notice;
        });
    }

    @Test
    void createsNoticeForAdmin() {
        NoticeResponse response = noticeAdminService.createNotice(
            1L,
            new NoticeCreateRequest("YOUR_TITLE", "YOUR_CONTENT"),
            "ops@hei.gg",
            "OpsUser"
        );

        assertThat(response.title()).isEqualTo("YOUR_TITLE");
        assertThat(response.content()).isEqualTo("YOUR_CONTENT");
        assertThat(response.authorNickname()).isEqualTo("OpsUser");
        verify(noticeRepository).save(any(Notice.class));
        verify(operationAuditLogService).recordNoticePosted(eq("ops@hei.gg"), eq("OpsUser"), eq(1L), any());
    }

    @Test
    void rejectsCreateWhenActorIsNotAdmin() {
        assertThatThrownBy(() ->
            noticeAdminService.createNotice(1L, new NoticeCreateRequest("t", "c"), "member@hei.gg", "Member")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Only admins can manage notices");

        verify(noticeRepository, never()).save(any(Notice.class));
    }

    @Test
    void rejectsCreateWithBlankTitle() {
        assertThatThrownBy(() ->
            noticeAdminService.createNotice(1L, new NoticeCreateRequest("  ", "content"), "ops@hei.gg", "OpsUser")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Title is required");
    }

    @Test
    void updatesExistingNotice() {
        Notice notice = new Notice();
        notice.setId(5L);
        notice.setGroupId(1L);
        notice.setTitle("old title");
        notice.setContent("old content");
        notice.setAuthorEmail("ops@hei.gg");
        when(noticeRepository.findByIdAndGroupId(5L, 1L)).thenReturn(Optional.of(notice));
        when(accessControlService.resolveAccessProfile("ops@hei.gg"))
            .thenReturn(new AccessControlService.AccessProfile(
                "ops@hei.gg", "OpsUser", "ADMIN", true, false, true, true, null
            ));

        NoticeResponse response = noticeAdminService.updateNotice(
            1L,
            5L,
            new NoticeUpdateRequest("new title", "new content"),
            "ops@hei.gg",
            "OpsUser"
        );

        assertThat(response.title()).isEqualTo("new title");
        assertThat(response.content()).isEqualTo("new content");
        verify(operationAuditLogService).recordNoticeUpdated(eq("ops@hei.gg"), eq("OpsUser"), eq(1L), any());
    }

    @Test
    void throwsWhenUpdatingMissingNotice() {
        when(noticeRepository.findByIdAndGroupId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            noticeAdminService.updateNotice(1L, 99L, new NoticeUpdateRequest("t", "c"), "ops@hei.gg", "OpsUser")
        )
            .isInstanceOf(java.util.NoSuchElementException.class)
            .hasMessage("Notice not found");
    }

    @Test
    void deletesExistingNotice() {
        Notice notice = new Notice();
        notice.setId(7L);
        notice.setGroupId(1L);
        notice.setTitle("to delete");
        when(noticeRepository.findByIdAndGroupId(7L, 1L)).thenReturn(Optional.of(notice));

        noticeAdminService.deleteNotice(1L, 7L, "ops@hei.gg", "OpsUser");

        verify(noticeRepository).delete(notice);
        verify(operationAuditLogService)
            .recordNoticeDeleted(eq("ops@hei.gg"), eq("OpsUser"), eq(1L), eq(7L), eq("to delete"));
    }
}
