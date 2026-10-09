package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.NoticeCreateRequest;
import com.balancify.backend.api.group.dto.NoticeResponse;
import com.balancify.backend.api.group.dto.NoticeUpdateRequest;
import com.balancify.backend.domain.Notice;
import com.balancify.backend.repository.NoticeEngagementRepository;
import com.balancify.backend.repository.NoticeRepository;
import com.balancify.backend.service.exception.NoticeImageException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
    private NoticeEngagementRepository noticeEngagementRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NoticeImageService noticeImageService;

    private NoticeAdminService noticeAdminService;

    @BeforeEach
    void setUp() {
        noticeAdminService = new NoticeAdminService(
            noticeRepository,
            noticeEngagementRepository,
            accessControlService,
            operationAuditLogService,
            notificationService,
            noticeImageService
        );
        when(accessControlService.isAdminEmail("ops@hei.gg")).thenReturn(true);
        when(accessControlService.isAdminEmail("member@hei.gg")).thenReturn(false);
        when(accessControlService.isAdminEmail("superadmin@hei.gg")).thenReturn(true);
        when(accessControlService.isSuperAdminEmail("superadmin@hei.gg")).thenReturn(true);
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
            new NoticeCreateRequest("YOUR_TITLE", "YOUR_CONTENT", null, null),
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
    void keepsANoticeToAdminsWhenAsked() {
        NoticeResponse response = noticeAdminService.createNotice(
            1L,
            new NoticeCreateRequest("YOUR_TITLE", "YOUR_CONTENT", true, null),
            "ops@hei.gg",
            "OpsUser"
        );

        ArgumentCaptor<Notice> saved = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(saved.capture());
        assertThat(saved.getValue().isAdminOnly()).isTrue();
        assertThat(response.adminOnly()).isTrue();
    }

    @Test
    void tellsReadersOfANewNoticeAndOfOneOpenedToMembersLater() {
        noticeAdminService.createNotice(1L, new NoticeCreateRequest("YOUR_TITLE", "YOUR_CONTENT", true, null), "ops@hei.gg", "OpsUser");
        verify(notificationService).publishNotice(1L, 1L, "YOUR_TITLE", true, "ops@hei.gg");

        Notice notice = new Notice();
        notice.setId(5L);
        notice.setGroupId(1L);
        notice.setTitle("YOUR_TITLE");
        notice.setContent("YOUR_CONTENT");
        notice.setAuthorEmail("ops@hei.gg");
        notice.setAdminOnly(true);
        when(noticeRepository.findByIdAndGroupIdForUpdate(5L, 1L)).thenReturn(Optional.of(notice));
        when(accessControlService.resolveAccessProfile("ops@hei.gg"))
            .thenReturn(new AccessControlService.AccessProfile(
                "ops@hei.gg", "OpsUser", "ADMIN", true, false, true, true, null
            ));

        noticeAdminService.updateNotice(1L, 5L, new NoticeUpdateRequest("YOUR_TITLE", "edited", null, null, null), "ops@hei.gg", "OpsUser");
        verify(notificationService, never()).publishNotice(eq(1L), eq(5L), any(), eq(false), any());
        noticeAdminService.updateNotice(1L, 5L, new NoticeUpdateRequest("YOUR_TITLE", "edited", false, null, null), "ops@hei.gg", "OpsUser");
        verify(notificationService).publishNotice(1L, 5L, "YOUR_TITLE", false, "ops@hei.gg");
    }

    @Test
    void anEditWithoutAnnouncingItChangesTheTextOnly() {
        Notice notice = existingNotice(5L, false);

        noticeAdminService.updateNotice(1L, 5L, new NoticeUpdateRequest("new title", "new content", null, null, null), "ops@hei.gg", "OpsUser");
        noticeAdminService.updateNotice(1L, 5L, new NoticeUpdateRequest("new title", "newer content", null, false, null), "ops@hei.gg", "OpsUser");

        assertThat(notice.getContent()).isEqualTo("newer content");
        assertThat(notice.getRevision()).isZero();
        assertThat(notice.getRevisedAt()).isNull();
        verify(noticeEngagementRepository, never()).markRead(any(), any(), any(), any());
        verify(notificationService, never()).publishNotice(any(), any(), any(), anyBoolean(), any());
        verify(notificationService, never()).publishNoticeRevised(any(), any(), any(), anyBoolean(), any());
        verify(operationAuditLogService, org.mockito.Mockito.times(2))
            .recordNoticeUpdated(eq("ops@hei.gg"), eq("OpsUser"), eq(1L), eq(notice), eq(false));
    }

    @Test
    void announcingAnEditAgainStartsARevisionAndTellsItsReaders() {
        Notice notice = existingNotice(5L, false);
        when(accessControlService.isAdminEmail(" Ops@hei.gg ")).thenReturn(true);

        noticeAdminService.updateNotice(1L, 5L, new NoticeUpdateRequest("new title", "new content", null, true, null), " Ops@hei.gg ", "OpsUser");

        assertThat(notice.getRevision()).isEqualTo(1);
        assertThat(notice.getRevisedAt()).isNotNull();
        // The editor's read is saved at the revision time itself: read, and with no point for it.
        verify(noticeEngagementRepository).markRead(5L, "ops@hei.gg", notice.getRevisedAt(), notice.getRevisedAt());
        verify(notificationService).publishNoticeRevised(1L, 5L, "new title", false, " Ops@hei.gg ");
        verify(notificationService, never()).publishNotice(any(), any(), any(), anyBoolean(), any());
        verify(operationAuditLogService).recordNoticeUpdated(eq(" Ops@hei.gg "), eq("OpsUser"), eq(1L), eq(notice), eq(true));

        noticeAdminService.updateNotice(1L, 5L, new NoticeUpdateRequest("new title", "again", null, true, null), "ops@hei.gg", "OpsUser");

        assertThat(notice.getRevision()).isEqualTo(2);
        verify(notificationService, org.mockito.Mockito.times(2)).publishNoticeRevised(eq(1L), eq(5L), any(), eq(false), any());
    }

    @Test
    void announcesAnAdminOnlyNoticeAgainToAdmins() {
        Notice notice = existingNotice(5L, true);

        noticeAdminService.updateNotice(1L, 5L, new NoticeUpdateRequest("t", "c", null, true, null), "ops@hei.gg", "OpsUser");

        assertThat(notice.getRevision()).isEqualTo(1);
        verify(notificationService).publishNoticeRevised(1L, 5L, "t", true, "ops@hei.gg");
    }

    @Test
    void aNoticeKeptToAdminsLeavesMembersNoNotificationOfIt() {
        Notice notice = existingNotice(5L, false);

        noticeAdminService.updateNotice(1L, 5L, new NoticeUpdateRequest("t", "c", true, null, null), "ops@hei.gg", "OpsUser");

        assertThat(notice.isAdminOnly()).isTrue();
        verify(notificationService).removeNotice(5L);
        verify(notificationService, never()).publishNotice(any(), any(), any(), anyBoolean(), any());
        verify(notificationService, never()).publishNoticeRevised(any(), any(), any(), anyBoolean(), any());

        // Announced again while being kept to admins, it is told to admins only, in place of the old one.
        Notice announced = existingNotice(6L, false);
        noticeAdminService.updateNotice(1L, 6L, new NoticeUpdateRequest("t", "c", true, true, null), "ops@hei.gg", "OpsUser");

        assertThat(announced.isAdminOnly()).isTrue();
        verify(notificationService).publishNoticeRevised(1L, 6L, "t", true, "ops@hei.gg");
        verify(notificationService, never()).removeNotice(6L);

        // An edit that leaves who may read it alone keeps its notification.
        existingNotice(7L, false);
        noticeAdminService.updateNotice(1L, 7L, new NoticeUpdateRequest("t", "c", false, null, null), "ops@hei.gg", "OpsUser");
        existingNotice(8L, true);
        noticeAdminService.updateNotice(1L, 8L, new NoticeUpdateRequest("t", "c", true, null, null), "ops@hei.gg", "OpsUser");

        verify(notificationService, never()).removeNotice(7L);
        verify(notificationService, never()).removeNotice(8L);
    }

    @Test
    void aNoticeOpenedToMembersIsNewToThemEvenWhenAnnouncedAgain() {
        Notice notice = existingNotice(5L, true);

        noticeAdminService.updateNotice(1L, 5L, new NoticeUpdateRequest("t", "c", false, true, null), "ops@hei.gg", "OpsUser");

        assertThat(notice.getRevision()).isZero();
        assertThat(notice.getRevisedAt()).isNull();
        verify(notificationService).publishNotice(1L, 5L, "t", false, "ops@hei.gg");
        verify(notificationService, never()).publishNoticeRevised(any(), any(), any(), anyBoolean(), any());
        verify(noticeEngagementRepository, never()).markRead(any(), any(), any(), any());
        verify(operationAuditLogService).recordNoticeUpdated(any(), any(), eq(1L), eq(notice), eq(false));
    }

    @Test
    void placesTheImagesItsTextNamesOnceTheNoticeIsSavedAndBeforeAnyoneIsTold() {
        noticeAdminService.createNotice(
            1L,
            new NoticeCreateRequest("YOUR_TITLE", "rules [[image:12]]", null, null),
            "ops@hei.gg",
            "OpsUser"
        );
        existingNotice(5L, false);
        noticeAdminService.updateNotice(
            1L, 5L, new NoticeUpdateRequest("YOUR_TITLE", "rules [[image:7]]", null, null, null), "ops@hei.gg", "OpsUser"
        );

        InOrder order = inOrder(noticeRepository, noticeImageService, operationAuditLogService, notificationService);
        order.verify(noticeRepository).save(any(Notice.class));
        order.verify(noticeImageService).placeInNotice(1L, 1L, "rules [[image:12]]");
        order.verify(operationAuditLogService).recordNoticePosted(any(), any(), any(), any());
        order.verify(notificationService).publishNotice(any(), any(), any(), anyBoolean(), any());
        order.verify(noticeRepository).save(any(Notice.class));
        order.verify(noticeImageService).placeInNotice(1L, 5L, "rules [[image:7]]");
        order.verify(operationAuditLogService).recordNoticeUpdated(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void aNoticeWhoseTextNamesAnImageItCannotShowIsNotPostedOrAnnounced() {
        doThrow(new NoticeImageException(NoticeImageException.Reason.UNAVAILABLE, "unavailable"))
            .when(noticeImageService).placeInNotice(any(), any(), any());
        existingNotice(5L, false);

        assertThatThrownBy(() -> noticeAdminService.createNotice(
            1L,
            new NoticeCreateRequest("YOUR_TITLE", "rules [[image:12]]", null, null),
            "ops@hei.gg",
            "OpsUser"
        )).isInstanceOf(NoticeImageException.class);
        assertThatThrownBy(() -> noticeAdminService.updateNotice(
            1L, 5L, new NoticeUpdateRequest("YOUR_TITLE", "rules [[image:12]]", null, true, null), "ops@hei.gg", "OpsUser"
        )).isInstanceOf(NoticeImageException.class);

        verify(operationAuditLogService, never()).recordNoticePosted(any(), any(), any(), any());
        verify(operationAuditLogService, never()).recordNoticeUpdated(any(), any(), any(), any(), anyBoolean());
        verify(notificationService, never()).publishNotice(any(), any(), any(), anyBoolean(), any());
        verify(notificationService, never()).publishNoticeRevised(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void aNoticeAsksForAVoteOnlyWhenToldTo() {
        NoticeResponse plain = noticeAdminService.createNotice(
            1L, new NoticeCreateRequest("YOUR_TITLE", "YOUR_CONTENT", null, null), "ops@hei.gg", "OpsUser"
        );
        NoticeResponse withVote = noticeAdminService.createNotice(
            1L, new NoticeCreateRequest("YOUR_TITLE", "YOUR_CONTENT", null, " open "), "ops@hei.gg", "OpsUser"
        );

        assertThat(plain.voteStatus()).isEqualTo("NONE");
        assertThat(withVote.voteStatus()).isEqualTo("OPEN");
        assertThatThrownBy(() -> noticeAdminService.createNotice(
            1L, new NoticeCreateRequest("YOUR_TITLE", "YOUR_CONTENT", null, "MAYBE"), "ops@hei.gg", "OpsUser"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEditOpensClosesOrRemovesTheVoteAndOtherwiseLeavesItAlone() {
        Notice notice = existingNotice(5L, false);

        noticeAdminService.updateNotice(1L, 5L, new NoticeUpdateRequest("t", "c", null, null, "OPEN"), "ops@hei.gg", "OpsUser");
        assertThat(notice.getVoteStatus()).isEqualTo("OPEN");
        // A typo fix that says nothing about the vote keeps it open.
        NoticeResponse edited = noticeAdminService.updateNotice(
            1L, 5L, new NoticeUpdateRequest("t", "c2", null, null, null), "ops@hei.gg", "OpsUser"
        );
        assertThat(notice.getVoteStatus()).isEqualTo("OPEN");
        assertThat(edited.voteStatus()).isEqualTo("OPEN");

        noticeAdminService.updateNotice(1L, 5L, new NoticeUpdateRequest("t", "c2", null, null, "CLOSED"), "ops@hei.gg", "OpsUser");
        assertThat(notice.getVoteStatus()).isEqualTo("CLOSED");
        noticeAdminService.updateNotice(1L, 5L, new NoticeUpdateRequest("t", "c2", null, null, "NONE"), "ops@hei.gg", "OpsUser");
        assertThat(notice.getVoteStatus()).isEqualTo("NONE");

        assertThatThrownBy(() -> noticeAdminService.updateNotice(
            1L, 5L, new NoticeUpdateRequest("t", "c2", null, null, "LATER"), "ops@hei.gg", "OpsUser"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThat(notice.getVoteStatus()).isEqualTo("NONE");
        // Votes already cast are not this service's to remove.
        verifyNoInteractions(noticeEngagementRepository);
    }

    @Test
    void rejectsCreateWhenActorIsNotAdmin() {
        assertThatThrownBy(() ->
            noticeAdminService.createNotice(1L, new NoticeCreateRequest("t", "c", null, null), "member@hei.gg", "Member")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Only admins can manage notices");

        verify(noticeRepository, never()).save(any(Notice.class));
    }

    @Test
    void rejectsCreateWithBlankTitle() {
        assertThatThrownBy(() ->
            noticeAdminService.createNotice(1L, new NoticeCreateRequest("  ", "content", null, null), "ops@hei.gg", "OpsUser")
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
        when(noticeRepository.findByIdAndGroupIdForUpdate(5L, 1L)).thenReturn(Optional.of(notice));
        when(accessControlService.resolveAccessProfile("ops@hei.gg"))
            .thenReturn(new AccessControlService.AccessProfile(
                "ops@hei.gg", "OpsUser", "ADMIN", true, false, true, true, null
            ));

        NoticeResponse response = noticeAdminService.updateNotice(
            1L,
            5L,
            new NoticeUpdateRequest("new title", "new content", null, null, null),
            "ops@hei.gg",
            "OpsUser"
        );

        assertThat(response.title()).isEqualTo("new title");
        assertThat(response.content()).isEqualTo("new content");
        verify(operationAuditLogService).recordNoticeUpdated(eq("ops@hei.gg"), eq("OpsUser"), eq(1L), any(), eq(false));
    }

    @Test
    void throwsWhenUpdatingMissingNotice() {
        when(noticeRepository.findByIdAndGroupIdForUpdate(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            noticeAdminService.updateNotice(1L, 99L, new NoticeUpdateRequest("t", "c", null, null, null), "ops@hei.gg", "OpsUser")
        )
            .isInstanceOf(java.util.NoSuchElementException.class)
            .hasMessage("Notice not found");
    }

    private Notice existingNotice(Long id, boolean adminOnly) {
        Notice notice = new Notice();
        notice.setId(id);
        notice.setGroupId(1L);
        notice.setTitle("old title");
        notice.setContent("old content");
        notice.setAuthorEmail("ops@hei.gg");
        notice.setAdminOnly(adminOnly);
        when(noticeRepository.findByIdAndGroupIdForUpdate(id, 1L)).thenReturn(Optional.of(notice));
        when(accessControlService.resolveAccessProfile("ops@hei.gg"))
            .thenReturn(new AccessControlService.AccessProfile(
                "ops@hei.gg", "OpsUser", "ADMIN", true, false, true, true, null
            ));
        return notice;
    }

    @Test
    void deletesExistingNotice() {
        Notice notice = new Notice();
        notice.setId(7L);
        notice.setGroupId(1L);
        notice.setTitle("to delete");
        when(noticeRepository.findByIdAndGroupId(7L, 1L)).thenReturn(Optional.of(notice));

        noticeAdminService.deleteNotice(1L, 7L, "superadmin@hei.gg", "SuperAdmin");

        verify(noticeRepository).delete(notice);
        verify(operationAuditLogService)
            .recordNoticeDeleted(eq("superadmin@hei.gg"), eq("SuperAdmin"), eq(1L), eq(7L), eq("to delete"));
    }

    @Test
    void rejectsDeleteWhenActorIsAdminButNotSuperAdmin() {
        Notice notice = new Notice();
        notice.setId(8L);
        notice.setGroupId(1L);
        notice.setTitle("keep");
        when(noticeRepository.findByIdAndGroupId(8L, 1L)).thenReturn(Optional.of(notice));

        assertThatThrownBy(() -> noticeAdminService.deleteNotice(1L, 8L, "ops@hei.gg", "OpsUser"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Only super admins can delete notices");

        verify(noticeRepository, never()).delete(any(Notice.class));
    }
}
