package com.balancify.backend.service;

import com.balancify.backend.repository.NoticeImageRepository;
import com.balancify.backend.repository.NoticeImageRepository.Placement;
import com.balancify.backend.repository.NoticeImageRepository.StoredImage;
import com.balancify.backend.service.exception.NoticeImageException;
import com.balancify.backend.service.exception.NoticeImageException.Reason;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Images inside notices. An admin uploads an image while writing and gets its id back; the page
 * puts a marker with that id into the text (NoticeImageTokens). Saving the notice places the
 * images its text names in it and removes those it no longer names. An image is shown to whoever
 * may open its notice; one not placed yet is shown to admins only, for the preview while writing.
 */
@Service
public class NoticeImageService {

    // Well under what the hosting in front of the API carries in one request or answer (about 4.5 MB).
    public static final int MAX_IMAGE_BYTES = 3 * 1024 * 1024;
    static final int MAX_IMAGES_PER_NOTICE = 10;
    // Uploads that no notice has taken yet, for the whole group.
    static final int MAX_UNPLACED_IMAGES = 30;
    static final Duration UNPLACED_LIFETIME = Duration.ofHours(24);

    private final NoticeImageRepository noticeImageRepository;
    private final AccessControlService accessControlService;

    public NoticeImageService(
        NoticeImageRepository noticeImageRepository,
        AccessControlService accessControlService
    ) {
        this.noticeImageRepository = noticeImageRepository;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public long upload(Long groupId, byte[] bytes, String actorEmail) {
        if (!accessControlService.isAdminEmail(actorEmail)) {
            throw new IllegalArgumentException("Only admins can upload notice images");
        }
        if (bytes != null && bytes.length > MAX_IMAGE_BYTES) {
            throw new NoticeImageException(Reason.TOO_LARGE, "이미지는 한 장에 3MB까지 올릴 수 있습니다.");
        }
        NoticeImageFormat format = NoticeImageFormat.detect(bytes)
            .orElseThrow(() -> new NoticeImageException(
                Reason.UNSUPPORTED,
                "PNG, JPG, WebP 이미지만 올릴 수 있습니다."
            ));

        // Uploads left behind by a notice that was never saved go first, so they do not count below.
        noticeImageRepository.deleteUnplacedBefore(OffsetDateTime.now().minus(UNPLACED_LIFETIME));
        if (noticeImageRepository.countUnplaced(groupId) >= MAX_UNPLACED_IMAGES) {
            throw new NoticeImageException(
                Reason.TOO_MANY_WAITING,
                "올려 두고 아직 공지에 쓰지 않은 이미지가 너무 많습니다. 공지를 저장한 뒤 다시 시도해 주세요."
            );
        }
        return noticeImageRepository.insert(groupId, format.contentType(), bytes);
    }

    /**
     * An image as it is served. openToMembers: it sits in a notice every member may open, which is
     * when a reader's own browser may keep a copy. One kept to admins, or not placed yet, is
     * checked on every request instead.
     */
    public record ReadableImage(String contentType, byte[] data, boolean openToMembers) {
    }

    /** An image nobody may see answers like one that is not there, as notices do. */
    @Transactional(readOnly = true)
    public ReadableImage read(Long groupId, Long imageId, String readerEmail) {
        Placement placement = noticeImageRepository.findPlacement(groupId, imageId)
            .orElseThrow(NoticeImageService::notFound);
        boolean adminsOnly = placement.noticeId() == null || placement.noticeAdminOnly();
        if (adminsOnly && !accessControlService.isAdminEmail(readerEmail)) {
            throw notFound();
        }
        StoredImage image = noticeImageRepository.findImage(imageId).orElseThrow(NoticeImageService::notFound);
        return new ReadableImage(image.contentType(), image.data(), !adminsOnly);
    }

    /**
     * Called while a notice is being saved, in that transaction. Every image the text names must be
     * unplaced or already in this notice: one that is gone, or that another notice holds, fails
     * the save. Images of this notice that the text dropped are removed.
     */
    public void placeInNotice(Long groupId, Long noticeId, String content) {
        List<Long> imageIds = NoticeImageTokens.imageIds(content);
        if (imageIds.size() > MAX_IMAGES_PER_NOTICE) {
            throw new NoticeImageException(
                Reason.TOO_MANY_IN_NOTICE,
                "이미지는 공지 하나에 " + MAX_IMAGES_PER_NOTICE + "장까지 넣을 수 있습니다."
            );
        }
        noticeImageRepository.place(groupId, noticeId, imageIds);
        if (noticeImageRepository.countPlaced(noticeId, imageIds) != imageIds.size()) {
            throw new NoticeImageException(
                Reason.UNAVAILABLE,
                "본문에 쓸 수 없는 이미지가 있습니다. 그 이미지를 지우고 다시 올려 주세요."
            );
        }
        noticeImageRepository.deletePlacedExcept(noticeId, imageIds);
    }

    private static NoSuchElementException notFound() {
        return new NoSuchElementException("Notice image not found");
    }
}
