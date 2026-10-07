package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.NoticeImageResponse;
import com.balancify.backend.repository.NoticeImageRepository.StoredImage;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.NoticeImageService;
import com.balancify.backend.service.exception.NoticeImageException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Images inside notices. Admins upload one as the request body itself, the image file and nothing
 * else. Members read an image of a notice they may open; the service decides that per image.
 */
@RestController
@RequestMapping("/api/groups")
public class GroupNoticeImageController {

    private final NoticeImageService noticeImageService;
    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public GroupNoticeImageController(
        NoticeImageService noticeImageService,
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.noticeImageService = noticeImageService;
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @PostMapping("/{groupId}/notice-images")
    public NoticeImageResponse uploadImage(
        @PathVariable Long groupId,
        HttpServletRequest httpRequest
    ) throws IOException {
        String requestEmail = requireRequestEmail(httpRequest);
        if (!accessControlService.isAdminEmail(requestEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }

        try {
            return new NoticeImageResponse(
                noticeImageService.upload(groupId, readBody(httpRequest), requestEmail)
            );
        } catch (NoticeImageException exception) {
            throw new ResponseStatusException(statusOf(exception), exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    @GetMapping("/{groupId}/notice-images/{imageId}")
    public ResponseEntity<byte[]> getImage(
        @PathVariable Long groupId,
        @PathVariable Long imageId,
        HttpServletRequest httpRequest
    ) {
        String requestEmail = requireRequestEmail(httpRequest);
        try {
            StoredImage image = noticeImageService.read(groupId, imageId, requestEmail);
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .body(image.data());
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    /** Reads one byte past the limit at most, so an oversized upload is refused without being held. */
    private byte[] readBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > NoticeImageService.MAX_IMAGE_BYTES) {
            throw new NoticeImageException(NoticeImageException.Reason.TOO_LARGE, "Notice image is too large");
        }
        return request.getInputStream().readNBytes(NoticeImageService.MAX_IMAGE_BYTES + 1);
    }

    static HttpStatus statusOf(NoticeImageException exception) {
        return switch (exception.getReason()) {
            case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case UNSUPPORTED -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case TOO_MANY_IN_NOTICE -> HttpStatus.BAD_REQUEST;
            case TOO_MANY_WAITING, UNAVAILABLE -> HttpStatus.CONFLICT;
        };
    }

    private String requireRequestEmail(HttpServletRequest request) {
        String requestEmail = authenticatedRequestResolver.resolve(request).email();
        if (requestEmail.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid Supabase bearer token is required");
        }
        return requestEmail;
    }
}
