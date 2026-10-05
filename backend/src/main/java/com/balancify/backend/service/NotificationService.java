package com.balancify.backend.service;

import com.balancify.backend.api.notification.dto.NotificationItemResponse;
import com.balancify.backend.api.notification.dto.NotificationListResponse;
import com.balancify.backend.api.notification.dto.PushConfigResponse;
import com.balancify.backend.api.notification.dto.PushSubscriptionRequest;
import com.balancify.backend.domain.Notification;
import com.balancify.backend.domain.NotificationCursor;
import com.balancify.backend.domain.PushSubscription;
import com.balancify.backend.repository.NotificationCursorRepository;
import com.balancify.backend.repository.NotificationRepository;
import com.balancify.backend.repository.PushSubscriptionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Notifications in the site and, for those who turned it on, Web Push to their browsers and
 * installed apps. A notification goes to MEMBERS (everyone with service access) or ADMINS; each
 * account keeps how far it has read. Pushes go out on their own thread after the commit.
 */
@Service
public class NotificationService {

    public static final String KIND_NOTICE = "NOTICE";
    public static final String KIND_PREDICTION = "PREDICTION";
    static final String AUDIENCE_MEMBERS = "MEMBERS";
    static final String AUDIENCE_ADMINS = "ADMINS";
    static final Duration RETENTION = Duration.ofDays(60);
    static final int MAX_SUBSCRIPTIONS_PER_ACCOUNT = 10;
    private static final int MAX_BODY_LENGTH = 300;
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);
    private static final Executor PUSH_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "web-push");
        thread.setDaemon(true);
        return thread;
    });

    private final NotificationRepository notificationRepository;
    private final NotificationCursorRepository notificationCursorRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final AccessControlService accessControlService;
    private final WebPushService webPushService;
    private final ObjectMapper objectMapper;
    private final boolean predictionsForMembers;
    private final boolean membersEnabled;
    private final Executor pushExecutor;
    private final Clock clock;

    @Autowired
    public NotificationService(
        NotificationRepository notificationRepository,
        NotificationCursorRepository notificationCursorRepository,
        PushSubscriptionRepository pushSubscriptionRepository,
        AccessControlService accessControlService,
        WebPushService webPushService,
        ObjectMapper objectMapper,
        @Value("${balancify.predictions.members-enabled:false}") boolean predictionsForMembers,
        @Value("${balancify.notifications.members-enabled:false}") boolean membersEnabled
    ) {
        this(
            notificationRepository,
            notificationCursorRepository,
            pushSubscriptionRepository,
            accessControlService,
            webPushService,
            objectMapper,
            predictionsForMembers,
            membersEnabled,
            PUSH_EXECUTOR,
            Clock.systemUTC()
        );
    }

    NotificationService(
        NotificationRepository notificationRepository,
        NotificationCursorRepository notificationCursorRepository,
        PushSubscriptionRepository pushSubscriptionRepository,
        AccessControlService accessControlService,
        WebPushService webPushService,
        ObjectMapper objectMapper,
        boolean predictionsForMembers,
        boolean membersEnabled,
        Executor pushExecutor,
        Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationCursorRepository = notificationCursorRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.accessControlService = accessControlService;
        this.webPushService = webPushService;
        this.objectMapper = objectMapper;
        this.predictionsForMembers = predictionsForMembers;
        this.membersEnabled = membersEnabled;
        this.pushExecutor = pushExecutor;
        this.clock = clock;
    }

    /**
     * Admins only while notifications are tried out; every member with service access once
     * balancify.notifications.members-enabled is on. Notifications for members are kept meanwhile,
     * so members see them when it opens.
     */
    public boolean canUseNotifications(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            return false;
        }
        return membersEnabled
            ? accessControlService.isServiceAccessAllowed(normalizedEmail)
            : accessControlService.isAdminEmail(normalizedEmail);
    }

    /** A notice members can now read (or, kept to admins, admins can). Replaces an earlier one for it. */
    @Transactional
    public void publishNotice(Long groupId, Long noticeId, String noticeTitle, boolean adminOnly, String authorEmail) {
        publishNotice(groupId, noticeId, "새 공지사항", noticeTitle, adminOnly, authorEmail);
    }

    /** An edited notice announced again to the people who can read it. Replaces the earlier one for it. */
    @Transactional
    public void publishNoticeRevised(Long groupId, Long noticeId, String noticeTitle, boolean adminOnly, String editorEmail) {
        publishNotice(groupId, noticeId, "공지사항 수정", noticeTitle, adminOnly, editorEmail);
    }

    private void publishNotice(
        Long groupId,
        Long noticeId,
        String heading,
        String noticeTitle,
        boolean adminOnly,
        String excludedEmail
    ) {
        notificationRepository.deleteByKindAndTargetId(KIND_NOTICE, noticeId);
        publish(
            groupId,
            KIND_NOTICE,
            noticeId,
            adminOnly ? AUDIENCE_ADMINS : AUDIENCE_MEMBERS,
            heading,
            noticeTitle,
            "/notices/" + noticeId,
            excludedEmail
        );
    }

    @Transactional
    public void removeNotice(Long noticeId) {
        notificationRepository.deleteByKindAndTargetId(KIND_NOTICE, noticeId);
    }

    /** A new balanced match is open for predictions; those who may predict hear of it. No player names go out. */
    @Transactional
    public void publishPredictionsOpen(Long groupId, Long matchId, int teamSize) {
        publish(
            groupId,
            KIND_PREDICTION,
            matchId,
            predictionsForMembers ? AUDIENCE_MEMBERS : AUDIENCE_ADMINS,
            "승부 예측",
            "새 " + teamSize + ":" + teamSize + " 경기의 승부 예측이 열렸습니다. 결과가 나오기 전에 이길 팀을 골라 보세요.",
            "/predictions",
            null
        );
    }

    /** The match was called off before it was played: nothing is left to predict. */
    @Transactional
    public void removePredictionsOpen(Long matchId) {
        notificationRepository.deleteByKindAndTargetId(KIND_PREDICTION, matchId);
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long groupId, String email) {
        String reader = normalizeEmail(email);
        return toResponse(visible(groupId, reader), lastReadId(reader));
    }

    @Transactional
    public NotificationListResponse markAllRead(Long groupId, String email) {
        String reader = normalizeEmail(email);
        List<Notification> notifications = visible(groupId, reader);
        long newest = notifications.stream().mapToLong(Notification::getId).max().orElse(0L);
        NotificationCursor cursor = notificationCursorRepository.findById(reader).orElseGet(() -> {
            NotificationCursor created = new NotificationCursor();
            created.setEmail(reader);
            return created;
        });
        if (newest > cursor.getLastReadId() || cursor.getLastReadId() == 0L) {
            cursor.setLastReadId(Math.max(newest, cursor.getLastReadId()));
            notificationCursorRepository.save(cursor);
        }
        return toResponse(notifications, cursor.getLastReadId());
    }

    public PushConfigResponse pushConfig() {
        return new PushConfigResponse(webPushService.isEnabled(), webPushService.publicKey());
    }

    /** Registers this browser for the account; the same endpoint moves over when another account signs in on it. */
    @Transactional
    public void subscribe(String email, PushSubscriptionRequest request) {
        String owner = normalizeEmail(email);
        if (!webPushService.isEnabled()) {
            throw new IllegalArgumentException("휴대폰·브라우저 알림이 아직 꺼져 있습니다.");
        }
        String endpoint = request == null || request.endpoint() == null ? "" : request.endpoint().trim();
        PushSubscriptionRequest.Keys keys = request == null ? null : request.keys();
        if (!WebPushService.isAllowedEndpoint(endpoint)) {
            throw new IllegalArgumentException("지원하지 않는 알림 주소입니다.");
        }
        if (keys == null || !WebPushCrypto.isValidSubscriptionKey(keys.p256dh(), keys.auth())) {
            throw new IllegalArgumentException("알림 키가 올바르지 않습니다.");
        }
        PushSubscription subscription = pushSubscriptionRepository.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        subscription.setEmail(owner);
        subscription.setEndpoint(endpoint);
        subscription.setP256dh(keys.p256dh().trim());
        subscription.setAuth(keys.auth().trim());
        pushSubscriptionRepository.save(subscription);

        List<PushSubscription> owned = pushSubscriptionRepository.findByEmailOrderByIdDesc(owner);
        if (owned.size() > MAX_SUBSCRIPTIONS_PER_ACCOUNT) {
            pushSubscriptionRepository.deleteAll(owned.subList(MAX_SUBSCRIPTIONS_PER_ACCOUNT, owned.size()));
        }
    }

    @Transactional
    public void unsubscribe(String email, String endpoint) {
        String owner = normalizeEmail(email);
        if (endpoint == null) {
            return;
        }
        pushSubscriptionRepository.findByEndpoint(endpoint.trim())
            .filter(subscription -> owner.equals(subscription.getEmail()))
            .ifPresent(pushSubscriptionRepository::delete);
    }

    private void publish(
        Long groupId,
        String kind,
        Long targetId,
        String audience,
        String title,
        String body,
        String link,
        String excludedEmail
    ) {
        Notification notification = new Notification();
        notification.setGroupId(groupId);
        notification.setKind(kind);
        notification.setTargetId(targetId);
        notification.setAudience(audience);
        notification.setTitle(title);
        notification.setBody(body == null ? null : body.length() > MAX_BODY_LENGTH ? body.substring(0, MAX_BODY_LENGTH) : body);
        notification.setLink(link);
        notificationRepository.save(notification);
        notificationRepository.deleteCreatedBefore(OffsetDateTime.now(clock).minus(RETENTION));

        if (!webPushService.isEnabled()) {
            return;
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("title", title);
        message.put("body", notification.getBody());
        message.put("link", link);
        message.put("tag", kind.toLowerCase(Locale.ROOT) + "-" + targetId);
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Push message could not be written", exception);
        }
        String excluded = normalizeEmail(excludedEmail);
        afterCommit(() -> push(audience, excluded, payload));
    }

    private void push(String audience, String excludedEmail, byte[] payload) {
        for (PushSubscription subscription : pushSubscriptionRepository.findAll()) {
            String email = normalizeEmail(subscription.getEmail());
            if (email.equals(excludedEmail) || !inAudience(audience, email)) {
                continue;
            }
            int status = webPushService.send(subscription, payload);
            if (status == 404 || status == 410) {
                pushSubscriptionRepository.deleteByEndpoint(subscription.getEndpoint());
            } else if (status < 200 || status >= 300) {
                LOGGER.info("Web Push answered {}", status);
            }
        }
    }

    private boolean inAudience(String audience, String email) {
        return AUDIENCE_ADMINS.equals(audience)
            ? accessControlService.isAdminEmail(email)
            : canUseNotifications(email);
    }

    private List<Notification> visible(Long groupId, String reader) {
        List<String> audiences = accessControlService.isAdminEmail(reader)
            ? List.of(AUDIENCE_MEMBERS, AUDIENCE_ADMINS)
            : List.of(AUDIENCE_MEMBERS);
        return notificationRepository.findTop30ByGroupIdAndAudienceInOrderByIdDesc(groupId, audiences);
    }

    private long lastReadId(String reader) {
        return notificationCursorRepository.findById(reader).map(NotificationCursor::getLastReadId).orElse(0L);
    }

    private NotificationListResponse toResponse(List<Notification> notifications, long lastReadId) {
        List<NotificationItemResponse> items = notifications.stream()
            .map(notification -> new NotificationItemResponse(
                notification.getId(),
                notification.getKind(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLink(),
                notification.getCreatedAt(),
                notification.getId() <= lastReadId
            ))
            .toList();
        int unread = (int) items.stream().filter(item -> !item.read()).count();
        return new NotificationListResponse(items, unread);
    }

    private void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            pushExecutor.execute(task);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                pushExecutor.execute(task);
            }
        });
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
