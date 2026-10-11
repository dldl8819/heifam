package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.balancify.backend.repository.CommentQuotaRepository;
import com.balancify.backend.service.exception.CommentLimitException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentQuotaServiceTest {

    private static final String MEMBER = "member@hei.gg";
    private static final String ADMIN = "ops@hei.gg";

    @Mock
    private CommentQuotaRepository commentQuotaRepository;

    @Mock
    private AccessControlService accessControlService;

    private CommentQuotaService at(String instant) {
        return new CommentQuotaService(
            commentQuotaRepository,
            accessControlService,
            Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)
        );
    }

    @Test
    void letsAMemberWriteTheTenthCommentOfTheDayAndHoldsThemWhileCounting() {
        when(commentQuotaRepository.countCommentsSince(eq(MEMBER), any())).thenReturn(9L);

        assertThatCode(() -> at("2026-10-12T03:00:00Z").reserve(MEMBER)).doesNotThrowAnyException();

        // Held first, so a comment written at the same moment is counted before this one is.
        InOrder order = inOrder(commentQuotaRepository);
        order.verify(commentQuotaRepository).lockCommenter(MEMBER);
        order.verify(commentQuotaRepository).countCommentsSince(eq(MEMBER), any());
    }

    @Test
    void refusesTheEleventhCommentOfTheDay() {
        when(commentQuotaRepository.countCommentsSince(eq(MEMBER), any())).thenReturn(10L);

        assertThatThrownBy(() -> at("2026-10-12T03:00:00Z").reserve(MEMBER))
            .isInstanceOf(CommentLimitException.class)
            .hasMessageContaining("10개");
        assertThat(CommentQuotaService.DAILY_COMMENT_LIMIT).isEqualTo(10);
    }

    @Test
    void countsTheDayFromMidnightInKorea() {
        when(commentQuotaRepository.countCommentsSince(eq(MEMBER), any())).thenReturn(0L);

        // 00:30 on the 12th in Korea is still the 11th in UTC; the second is 23:59 on the 11th in Korea.
        at("2026-10-11T15:30:00Z").reserve(MEMBER);
        at("2026-10-11T14:59:00Z").reserve(MEMBER);

        ArgumentCaptor<OffsetDateTime> since = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(commentQuotaRepository, times(2)).countCommentsSince(eq(MEMBER), since.capture());
        assertThat(since.getAllValues()).extracting(OffsetDateTime::toInstant).containsExactly(
            Instant.parse("2026-10-11T15:00:00Z"),
            Instant.parse("2026-10-10T15:00:00Z")
        );
    }

    @Test
    void doesNotCapAdmins() {
        when(accessControlService.isAdminEmail(ADMIN)).thenReturn(true);

        assertThatCode(() -> at("2026-10-12T03:00:00Z").reserve(ADMIN)).doesNotThrowAnyException();

        verifyNoInteractions(commentQuotaRepository);
    }

    @Test
    void countsTheWriterHoweverTheirEmailIsWritten() {
        when(commentQuotaRepository.countCommentsSince(eq(MEMBER), any())).thenReturn(10L);

        assertThatThrownBy(() -> at("2026-10-12T03:00:00Z").reserve("  Member@HEI.gg "))
            .isInstanceOf(CommentLimitException.class);

        verify(accessControlService).isAdminEmail(MEMBER);
        verify(commentQuotaRepository).lockCommenter(MEMBER);
    }
}
