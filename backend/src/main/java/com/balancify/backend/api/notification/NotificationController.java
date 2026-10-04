package com.balancify.backend.api.notification;

import com.balancify.backend.api.notification.dto.NotificationListResponse;
import com.balancify.backend.api.notification.dto.PushConfigResponse;
import com.balancify.backend.api.notification.dto.PushSubscriptionRequest;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Notifications, for admins while they are tried out (AdminKeyFilter checks the same) and for
 * every member once balancify.notifications.members-enabled and the routes open; which ones an
 * account sees depends on its role. Push subscriptions belong to the signed-in account.
 */
@RestController
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public NotificationController(
        NotificationService notificationService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.notificationService = notificationService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping("/api/groups/{groupId}/notifications")
    public NotificationListResponse list(@PathVariable Long groupId, HttpServletRequest request) {
        return notificationService.list(groupId, requireEmail(request));
    }

    @PostMapping("/api/groups/{groupId}/notifications/read")
    public NotificationListResponse markAllRead(@PathVariable Long groupId, HttpServletRequest request) {
        return notificationService.markAllRead(groupId, requireEmail(request));
    }

    @GetMapping("/api/notifications/push-config")
    public PushConfigResponse pushConfig(HttpServletRequest request) {
        requireEmail(request);
        return notificationService.pushConfig();
    }

    @PostMapping("/api/notifications/push-subscriptions")
    public void subscribe(@RequestBody PushSubscriptionRequest requestBody, HttpServletRequest request) {
        String email = requireEmail(request);
        try {
            notificationService.subscribe(email, requestBody);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/api/notifications/push-subscriptions/remove")
    public void unsubscribe(@RequestBody PushSubscriptionRequest requestBody, HttpServletRequest request) {
        notificationService.unsubscribe(requireEmail(request), requestBody == null ? null : requestBody.endpoint());
    }

    private String requireEmail(HttpServletRequest request) {
        String email = authenticatedRequestResolver.resolve(request).email();
        if (email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid Supabase bearer token is required");
        }
        if (!notificationService.canUseNotifications(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Notifications are not open to this account yet");
        }
        return email;
    }
}
