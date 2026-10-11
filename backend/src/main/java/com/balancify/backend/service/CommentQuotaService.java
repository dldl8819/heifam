package com.balancify.backend.service;

import com.balancify.backend.repository.CommentQuotaRepository;
import com.balancify.backend.service.exception.CommentLimitException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caps how many comments one person writes per KST day: on notices and on every board together,
 * replies included, from any of their emails. A comment removed again no longer counts. Admins
 * are not capped: they answer members, on notices and on the anonymous board.
 *
 * <p>Comments are counted from their own tables, which the caller writes in the same transaction.
 * Holding the person until that transaction ends keeps two comments from both taking the last
 * slot.
 */
@Service
public class CommentQuotaService {

    public static final int DAILY_COMMENT_LIMIT = 10;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CommentQuotaRepository commentQuotaRepository;
    private final AccessControlService accessControlService;
    private final Clock clock;

    @Autowired
    public CommentQuotaService(CommentQuotaRepository commentQuotaRepository, AccessControlService accessControlService) {
        this(commentQuotaRepository, accessControlService, Clock.system(KST));
    }

    CommentQuotaService(
        CommentQuotaRepository commentQuotaRepository,
        AccessControlService accessControlService,
        Clock clock
    ) {
        this.commentQuotaRepository = commentQuotaRepository;
        this.accessControlService = accessControlService;
        this.clock = clock;
    }

    /** Lets the writer comment once more today, or refuses when today's comments are used up. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(String authorEmail) {
        String email = authorEmail == null ? "" : authorEmail.trim().toLowerCase(Locale.ROOT);
        if (accessControlService.isAdminEmail(email)) {
            return;
        }
        commentQuotaRepository.lockCommenter(email);
        OffsetDateTime startOfToday = LocalDate.now(clock.withZone(KST)).atStartOfDay(KST).toOffsetDateTime();
        if (commentQuotaRepository.countCommentsSince(email, startOfToday) >= DAILY_COMMENT_LIMIT) {
            throw new CommentLimitException(
                "댓글은 공지와 게시판을 합쳐 하루 " + DAILY_COMMENT_LIMIT + "개까지 쓸 수 있습니다. "
                    + "한국 시간 0시가 지나면 다시 쓸 수 있습니다."
            );
        }
    }
}
