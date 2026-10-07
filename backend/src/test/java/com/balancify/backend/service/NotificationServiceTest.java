package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.notification.dto.NotificationListResponse;
import com.balancify.backend.api.notification.dto.PushSubscriptionRequest;
import com.balancify.backend.domain.Notification;
import com.balancify.backend.domain.NotificationCursor;
import com.balancify.backend.domain.PushSubscription;
import com.balancify.backend.repository.NotificationCursorRepository;
import com.balancify.backend.repository.NotificationRepository;
import com.balancify.backend.repository.PushSubscriptionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    private static final String ADMIN = "ops@hei.gg";
    private static final String MEMBER = "member@hei.gg";
    private static final String OUTSIDER = "blocked@hei.gg";
    // RFC 8291 example keys stand in for a browser's subscription.
    private static final String P256DH = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationCursorRepository notificationCursorRepository;

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private WebPushService webPushService;

    private NotificationService service;
    private final List<Notification> saved = new ArrayList<>();
    private final List<byte[]> pushed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = createService(false, false);
        when(accessControlService.isAdminEmail(ADMIN)).thenReturn(true);
        when(accessControlService.isServiceAccessAllowed(ADMIN)).thenReturn(true);
        when(accessControlService.isServiceAccessAllowed(MEMBER)).thenReturn(true);
        when(webPushService.isEnabled()).thenReturn(true);
        when(webPushService.publicKey()).thenReturn("YOUR_API_KEY");
        when(webPushService.send(any(), any())).thenAnswer(invocation -> {
            pushed.add(invocation.getArgument(1));
            return 201;
        });
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", (long) saved.size() + 1);
            saved.add(notification);
            return notification;
        });
        when(pushSubscriptionRepository.findAll()).thenReturn(List.of(
            subscription(ADMIN, "https://fcm.googleapis.com/fcm/send/admin"),
            subscription(MEMBER, "https://updates.push.services.mozilla.com/wpush/v2/member"),
            subscription(OUTSIDER, "https://web.push.apple.com/outsider")
        ));
    }

    @Test
    void sendsANewNoticeToMembersButNotToItsAuthor() {
        service = createService(false, true);
        service.publishNotice(1L, 9L, "YOUR_TITLE", false, ADMIN);

        verify(notificationRepository).deleteByKindAndTargetId(NotificationService.KIND_NOTICE, 9L);
        assertThat(saved).singleElement().satisfies(notification -> {
            assertThat(notification.getAudience()).isEqualTo("MEMBERS");
            assertThat(notification.getBody()).isEqualTo("YOUR_TITLE");
            assertThat(notification.getLink()).isEqualTo("/notices/9");
        });
        ArgumentCaptor<PushSubscription> targets = ArgumentCaptor.forClass(PushSubscription.class);
        verify(webPushService).send(targets.capture(), any());
        assertThat(targets.getAllValues()).extracting(PushSubscription::getEmail).containsExactly(MEMBER);
        assertThat(new String(pushed.getFirst(), StandardCharsets.UTF_8))
            .isEqualTo("{\"title\":\"새 공지사항\",\"body\":\"YOUR_TITLE\",\"link\":\"/notices/9\",\"tag\":\"notice-9\"}");
    }

    @Test
    void announcesAnEditedNoticeAgainInPlaceOfItsEarlierNotification() {
        service = createService(false, true);
        service.publishNoticeRevised(1L, 9L, "YOUR_TITLE", false, ADMIN);

        verify(notificationRepository).deleteByKindAndTargetId(NotificationService.KIND_NOTICE, 9L);
        assertThat(saved).singleElement().satisfies(notification -> {
            assertThat(notification.getAudience()).isEqualTo("MEMBERS");
            assertThat(notification.getTitle()).isEqualTo("공지사항 수정");
            assertThat(notification.getBody()).isEqualTo("YOUR_TITLE");
            assertThat(notification.getLink()).isEqualTo("/notices/9");
        });
        ArgumentCaptor<PushSubscription> targets = ArgumentCaptor.forClass(PushSubscription.class);
        verify(webPushService).send(targets.capture(), any());
        assertThat(targets.getAllValues()).extracting(PushSubscription::getEmail).containsExactly(MEMBER);
        // The same tag as the first announcement, so a phone shows the edit in its place.
        assertThat(new String(pushed.getFirst(), StandardCharsets.UTF_8))
            .isEqualTo("{\"title\":\"공지사항 수정\",\"body\":\"YOUR_TITLE\",\"link\":\"/notices/9\",\"tag\":\"notice-9\"}");
    }

    @Test
    void announcesAnEditedAdminOnlyNoticeToAdmins() {
        service.publishNoticeRevised(1L, 9L, "YOUR_TITLE", true, null);

        assertThat(saved).singleElement().satisfies(notification -> assertThat(notification.getAudience()).isEqualTo("ADMINS"));
    }

    @Test
    void keepsAdminOnlyNoticesAndTrialPredictionsToAdmins() {
        service.publishNotice(1L, 9L, "YOUR_TITLE", true, MEMBER);
        service.publishPredictionsOpen(1L, 40L, 3);

        assertThat(saved).extracting(Notification::getAudience).containsExactly("ADMINS", "ADMINS");
        assertThat(saved.getLast().getBody()).startsWith("새 3:3 경기의 승부 예측이 열렸습니다");
        ArgumentCaptor<PushSubscription> targets = ArgumentCaptor.forClass(PushSubscription.class);
        verify(webPushService, org.mockito.Mockito.times(2)).send(targets.capture(), any());
        assertThat(targets.getAllValues()).extracting(PushSubscription::getEmail).containsOnly(ADMIN);
    }

    @Test
    void keepsNotificationsToAdminsWhileTheyAreTriedOut() {
        service.publishNotice(1L, 9L, "YOUR_TITLE", false, null);

        assertThat(saved).singleElement().satisfies(notification -> assertThat(notification.getAudience()).isEqualTo("MEMBERS"));
        ArgumentCaptor<PushSubscription> targets = ArgumentCaptor.forClass(PushSubscription.class);
        verify(webPushService).send(targets.capture(), any());
        assertThat(targets.getAllValues()).extracting(PushSubscription::getEmail).containsExactly(ADMIN);
        assertThat(service.canUseNotifications(ADMIN)).isTrue();
        assertThat(service.canUseNotifications(MEMBER)).isFalse();
        assertThat(createService(false, true).canUseNotifications(MEMBER)).isTrue();
        assertThat(createService(false, true).canUseNotifications(OUTSIDER)).isFalse();
    }

    @Test
    void opensPredictionNotificationsToMembersWithTheFlag() {
        createService(true, true).publishPredictionsOpen(1L, 40L, 2);

        assertThat(saved).singleElement().satisfies(notification -> assertThat(notification.getAudience()).isEqualTo("MEMBERS"));
    }

    @Test
    void forgetsSubscriptionsAPushServiceSaysAreGone() {
        service = createService(false, true);
        when(webPushService.send(any(), any())).thenReturn(410);

        service.publishNotice(1L, 9L, "YOUR_TITLE", false, null);

        verify(pushSubscriptionRepository).deleteByEndpoint("https://fcm.googleapis.com/fcm/send/admin");
        verify(pushSubscriptionRepository).deleteByEndpoint("https://updates.push.services.mozilla.com/wpush/v2/member");
        verify(pushSubscriptionRepository, never()).deleteByEndpoint("https://web.push.apple.com/outsider");
    }

    @Test
    void keepsNotificationsInTheSiteWhilePushIsOff() {
        when(webPushService.isEnabled()).thenReturn(false);

        service.publishNotice(1L, 9L, "YOUR_TITLE", false, null);

        assertThat(saved).hasSize(1);
        verify(webPushService, never()).send(any(), any());
    }

    @Test
    void showsMembersTheirNotificationsWithWhatTheyHaveRead() {
        when(notificationRepository.findTop30ByGroupIdAndAudienceInOrderByIdDesc(eq(1L), anyCollection()))
            .thenReturn(List.of(notification(7L), notification(6L), notification(5L)));
        NotificationCursor cursor = new NotificationCursor();
        cursor.setEmail(MEMBER);
        cursor.setLastReadId(5L);
        when(notificationCursorRepository.findById(MEMBER)).thenReturn(Optional.of(cursor));

        NotificationListResponse list = service.list(1L, " Member@hei.gg ");

        verify(notificationRepository).findTop30ByGroupIdAndAudienceInOrderByIdDesc(1L, List.of("MEMBERS"));
        assertThat(list.unreadCount()).isEqualTo(2);
        assertThat(list.notifications()).extracting(item -> item.read()).containsExactly(false, false, true);

        service.list(1L, ADMIN);
        verify(notificationRepository).findTop30ByGroupIdAndAudienceInOrderByIdDesc(1L, List.of("MEMBERS", "ADMINS"));
    }

    @Test
    void marksEverythingShownAsRead() {
        when(notificationRepository.findTop30ByGroupIdAndAudienceInOrderByIdDesc(eq(1L), anyCollection()))
            .thenReturn(List.of(notification(7L), notification(6L)));
        when(notificationCursorRepository.findById(MEMBER)).thenReturn(Optional.empty());

        NotificationListResponse list = service.markAllRead(1L, MEMBER);

        ArgumentCaptor<NotificationCursor> cursor = ArgumentCaptor.forClass(NotificationCursor.class);
        verify(notificationCursorRepository).save(cursor.capture());
        assertThat(cursor.getValue().getEmail()).isEqualTo(MEMBER);
        assertThat(cursor.getValue().getLastReadId()).isEqualTo(7L);
        assertThat(list.unreadCount()).isZero();
    }

    @Test
    void registersABrowserOnlyForAPushServiceWithUsableKeys() {
        when(pushSubscriptionRepository.findByEndpoint(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.subscribe(MEMBER, request("http://127.0.0.1:8080/internal", P256DH, AUTH)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.subscribe(MEMBER, request("https://fcm.googleapis.com.example.com/x", P256DH, AUTH)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.subscribe(MEMBER, request("https://fcm.googleapis.com/fcm/send/x", "bad", AUTH)))
            .isInstanceOf(IllegalArgumentException.class);

        service.subscribe(MEMBER, request("https://fcm.googleapis.com/fcm/send/x", P256DH, AUTH));

        ArgumentCaptor<PushSubscription> saved = ArgumentCaptor.forClass(PushSubscription.class);
        verify(pushSubscriptionRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(MEMBER);
        assertThat(saved.getValue().getEndpoint()).isEqualTo("https://fcm.googleapis.com/fcm/send/x");
    }

    @Test
    void refusesToRegisterWhilePushIsOff() {
        when(webPushService.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.subscribe(MEMBER, request("https://fcm.googleapis.com/fcm/send/x", P256DH, AUTH)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.pushConfig().enabled()).isFalse();
    }

    @Test
    void movesABrowserToTheAccountNowSignedInAndKeepsTenPerAccount() {
        PushSubscription existing = subscription(ADMIN, "https://fcm.googleapis.com/fcm/send/x");
        when(pushSubscriptionRepository.findByEndpoint("https://fcm.googleapis.com/fcm/send/x")).thenReturn(Optional.of(existing));
        List<PushSubscription> owned = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            owned.add(subscription(MEMBER, "https://fcm.googleapis.com/fcm/send/" + index));
        }
        when(pushSubscriptionRepository.findByEmailOrderByIdDesc(MEMBER)).thenReturn(owned);

        service.subscribe(MEMBER, request("https://fcm.googleapis.com/fcm/send/x", P256DH, AUTH));

        assertThat(existing.getEmail()).isEqualTo(MEMBER);
        verify(pushSubscriptionRepository).deleteAll(List.of(owned.getLast()));
    }

    @Test
    void letsOnlyTheOwnerRemoveABrowser() {
        PushSubscription existing = subscription(ADMIN, "https://fcm.googleapis.com/fcm/send/x");
        when(pushSubscriptionRepository.findByEndpoint("https://fcm.googleapis.com/fcm/send/x")).thenReturn(Optional.of(existing));

        service.unsubscribe(MEMBER, "https://fcm.googleapis.com/fcm/send/x");
        verify(pushSubscriptionRepository, never()).delete(any(PushSubscription.class));

        service.unsubscribe(ADMIN, "https://fcm.googleapis.com/fcm/send/x");
        verify(pushSubscriptionRepository).delete(existing);
    }

    @Test
    void allowsOnlyBrowserPushServices() {
        assertThat(WebPushService.isAllowedEndpoint("https://fcm.googleapis.com/fcm/send/x")).isTrue();
        assertThat(WebPushService.isAllowedEndpoint("https://web.push.apple.com/QGuQ")).isTrue();
        assertThat(WebPushService.isAllowedEndpoint("https://wns2-par02p.notify.windows.com/w/?token=x")).isTrue();
        assertThat(WebPushService.isAllowedEndpoint("http://fcm.googleapis.com/fcm/send/x")).isFalse();
        assertThat(WebPushService.isAllowedEndpoint("https://user@fcm.googleapis.com/fcm/send/x")).isFalse();
        assertThat(WebPushService.isAllowedEndpoint("https://evilpush.apple.com.example.org/x")).isFalse();
        assertThat(WebPushService.isAllowedEndpoint("https://localhost/x")).isFalse();
    }

    private NotificationService createService(boolean predictionsForMembers, boolean membersEnabled) {
        return new NotificationService(
            notificationRepository,
            notificationCursorRepository,
            pushSubscriptionRepository,
            accessControlService,
            webPushService,
            JsonMapper.builder().build(),
            predictionsForMembers,
            membersEnabled,
            Runnable::run,
            Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    private Notification notification(long id) {
        Notification notification = new Notification();
        ReflectionTestUtils.setField(notification, "id", id);
        notification.setGroupId(1L);
        notification.setKind(NotificationService.KIND_NOTICE);
        notification.setAudience("MEMBERS");
        notification.setTitle("새 공지사항");
        return notification;
    }

    private PushSubscription subscription(String email, String endpoint) {
        PushSubscription subscription = new PushSubscription();
        subscription.setEmail(email);
        subscription.setEndpoint(endpoint);
        subscription.setP256dh(P256DH);
        subscription.setAuth(AUTH);
        return subscription;
    }

    private PushSubscriptionRequest request(String endpoint, String p256dh, String auth) {
        return new PushSubscriptionRequest(endpoint, new PushSubscriptionRequest.Keys(p256dh, auth));
    }
}
