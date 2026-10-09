package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.repository.NoticeImageRepository;
import com.balancify.backend.repository.NoticeImageRepository.Placement;
import com.balancify.backend.repository.NoticeImageRepository.StoredImage;
import com.balancify.backend.service.exception.NoticeImageException;
import com.balancify.backend.service.exception.NoticeImageException.Reason;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
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
class NoticeImageServiceTest {

    private static final byte[] PNG = NoticeImageFormatTest.PNG;

    @Mock
    private NoticeImageRepository noticeImageRepository;

    @Mock
    private AccessControlService accessControlService;

    private NoticeImageService noticeImageService;

    @BeforeEach
    void setUp() {
        noticeImageService = new NoticeImageService(noticeImageRepository, accessControlService);
        when(accessControlService.isAdminEmail("ops@hei.gg")).thenReturn(true);
        when(accessControlService.isAdminEmail("member@hei.gg")).thenReturn(false);
        when(noticeImageRepository.findImage(9L)).thenReturn(Optional.of(new StoredImage("image/png", PNG)));
    }

    @Test
    void keepsAnAdminsImageAsTheKindItsBytesSay() {
        when(noticeImageRepository.insert(1L, "image/png", PNG)).thenReturn(9L);

        assertThat(noticeImageService.upload(1L, PNG, "ops@hei.gg")).isEqualTo(9L);

        verify(noticeImageRepository).insert(1L, "image/png", PNG);
    }

    @Test
    void takesNoUploadFromAMember() {
        assertThatThrownBy(() -> noticeImageService.upload(1L, PNG, "member@hei.gg"))
            .isInstanceOf(IllegalArgumentException.class);

        verify(noticeImageRepository, never()).insert(any(), any(), any());
    }

    @Test
    void refusesWhatIsNotAnImageOrIsTooLarge() {
        byte[] tooLarge = Arrays.copyOf(PNG, NoticeImageService.MAX_IMAGE_BYTES + 1);
        byte[] largestAllowed = Arrays.copyOf(PNG, NoticeImageService.MAX_IMAGE_BYTES);
        when(noticeImageRepository.insert(any(), any(), any())).thenReturn(9L);

        assertThat(reasonOf(() -> noticeImageService.upload(1L, "not an image".getBytes(), "ops@hei.gg")))
            .isEqualTo(Reason.UNSUPPORTED);
        assertThat(reasonOf(() -> noticeImageService.upload(1L, new byte[0], "ops@hei.gg")))
            .isEqualTo(Reason.UNSUPPORTED);
        assertThat(reasonOf(() -> noticeImageService.upload(1L, tooLarge, "ops@hei.gg")))
            .isEqualTo(Reason.TOO_LARGE);
        verify(noticeImageRepository, never()).insert(any(), any(), any());

        assertThat(noticeImageService.upload(1L, largestAllowed, "ops@hei.gg")).isEqualTo(9L);
    }

    @Test
    void clearsUploadsNoNoticeTookInADayBeforeCountingThoseStillWaiting() {
        when(noticeImageRepository.countUnplaced(1L)).thenReturn(NoticeImageService.MAX_UNPLACED_IMAGES - 1);
        when(noticeImageRepository.insert(any(), any(), any())).thenReturn(9L);

        noticeImageService.upload(1L, PNG, "ops@hei.gg");

        ArgumentCaptor<OffsetDateTime> uploadedBefore = ArgumentCaptor.forClass(OffsetDateTime.class);
        InOrder order = inOrder(noticeImageRepository);
        order.verify(noticeImageRepository).deleteUnplacedBefore(uploadedBefore.capture());
        order.verify(noticeImageRepository).countUnplaced(1L);
        order.verify(noticeImageRepository).insert(1L, "image/png", PNG);
        assertThat(uploadedBefore.getValue())
            .isCloseTo(OffsetDateTime.now().minusHours(24), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void refusesAnUploadWhileTooManyAreWaitingForANotice() {
        when(noticeImageRepository.countUnplaced(1L)).thenReturn(NoticeImageService.MAX_UNPLACED_IMAGES);

        assertThat(reasonOf(() -> noticeImageService.upload(1L, PNG, "ops@hei.gg")))
            .isEqualTo(Reason.TOO_MANY_WAITING);

        verify(noticeImageRepository, never()).insert(any(), any(), any());
    }

    @Test
    void showsAMemberTheImagesOfANoticeOpenToMembers() {
        when(noticeImageRepository.findPlacement(1L, 9L)).thenReturn(Optional.of(new Placement(5L, false)));

        NoticeImageService.ReadableImage image = noticeImageService.read(1L, 9L, "member@hei.gg");

        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.data()).isEqualTo(PNG);
        // Every member may see it, admins included, so anyone's browser may keep its copy.
        assertThat(image.openToMembers()).isTrue();
        assertThat(noticeImageService.read(1L, 9L, "ops@hei.gg").openToMembers()).isTrue();
    }

    @Test
    void hidesFromMembersTheImagesOfANoticeKeptToAdmins() {
        when(noticeImageRepository.findPlacement(1L, 9L)).thenReturn(Optional.of(new Placement(5L, true)));

        assertThatThrownBy(() -> noticeImageService.read(1L, 9L, "member@hei.gg"))
            .isInstanceOf(NoSuchElementException.class);
        verify(noticeImageRepository, never()).findImage(anyLong());

        NoticeImageService.ReadableImage forAdmin = noticeImageService.read(1L, 9L, "ops@hei.gg");
        assertThat(forAdmin.data()).isEqualTo(PNG);
        // Checked on every request: who is an admin can change, and so can the notice.
        assertThat(forAdmin.openToMembers()).isFalse();
    }

    @Test
    void showsAnImageNoNoticeHasTakenYetToAdminsOnly() {
        when(noticeImageRepository.findPlacement(1L, 9L)).thenReturn(Optional.of(new Placement(null, false)));

        assertThatThrownBy(() -> noticeImageService.read(1L, 9L, "member@hei.gg"))
            .isInstanceOf(NoSuchElementException.class);
        verify(noticeImageRepository, never()).findImage(anyLong());

        NoticeImageService.ReadableImage forAdmin = noticeImageService.read(1L, 9L, "ops@hei.gg");
        assertThat(forAdmin.data()).isEqualTo(PNG);
        assertThat(forAdmin.openToMembers()).isFalse();
    }

    @Test
    void answersNotFoundForAnImageThatIsNotInTheGroup() {
        when(noticeImageRepository.findPlacement(2L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noticeImageService.read(2L, 9L, "ops@hei.gg"))
            .isInstanceOf(NoSuchElementException.class);
        verify(noticeImageRepository, never()).findImage(anyLong());
    }

    @Test
    void placesTheImagesATextNamesAndRemovesThoseItDropped() {
        when(noticeImageRepository.countPlaced(5L, List.of(12L, 7L))).thenReturn(2);

        noticeImageService.placeInNotice(1L, 5L, "a [[image:12]] b [[image:7]] c [[image:12]]");

        InOrder order = inOrder(noticeImageRepository);
        order.verify(noticeImageRepository).place(1L, 5L, List.of(12L, 7L));
        order.verify(noticeImageRepository).countPlaced(5L, List.of(12L, 7L));
        order.verify(noticeImageRepository).deletePlacedExcept(5L, List.of(12L, 7L));
    }

    @Test
    void removesEveryImageOfANoticeWhoseTextNamesNone() {
        noticeImageService.placeInNotice(1L, 5L, "text only");

        verify(noticeImageRepository).deletePlacedExcept(5L, List.of());
    }

    @Test
    void failsTheSaveWhenANamedImageIsGoneOrBelongsToAnotherNotice() {
        when(noticeImageRepository.countPlaced(5L, List.of(12L, 7L))).thenReturn(1);

        assertThat(reasonOf(() -> noticeImageService.placeInNotice(1L, 5L, "[[image:12]] [[image:7]]")))
            .isEqualTo(Reason.UNAVAILABLE);

        verify(noticeImageRepository, never()).deletePlacedExcept(any(), any());
    }

    @Test
    void failsTheSaveWhenTheTextNamesMoreImagesThanANoticeMayHold() {
        String atTheLimit = markers(NoticeImageService.MAX_IMAGES_PER_NOTICE);
        String overTheLimit = markers(NoticeImageService.MAX_IMAGES_PER_NOTICE + 1);
        when(noticeImageRepository.countPlaced(any(), any())).thenReturn(NoticeImageService.MAX_IMAGES_PER_NOTICE);

        noticeImageService.placeInNotice(1L, 5L, atTheLimit);
        assertThat(reasonOf(() -> noticeImageService.placeInNotice(1L, 6L, overTheLimit)))
            .isEqualTo(Reason.TOO_MANY_IN_NOTICE);

        verify(noticeImageRepository, never()).place(any(), eq(6L), any());
    }

    private static String markers(int count) {
        return LongStream.rangeClosed(1, count)
            .mapToObj(id -> "[[image:" + id + "]]")
            .collect(Collectors.joining("\n"));
    }

    private static Reason reasonOf(Runnable action) {
        try {
            action.run();
        } catch (NoticeImageException exception) {
            return exception.getReason();
        }
        return null;
    }
}
