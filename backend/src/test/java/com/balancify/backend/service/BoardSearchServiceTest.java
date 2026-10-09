package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.BoardSearchItemResponse;
import com.balancify.backend.api.group.dto.BoardSearchResponse;
import com.balancify.backend.repository.BoardSearchRepository;
import com.balancify.backend.repository.BoardSearchRepository.FoundRow;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoardSearchServiceTest {

    private static final String ADMIN = "ops@hei.gg";
    private static final String MEMBER = "member@hei.gg";
    private static final OffsetDateTime WRITTEN = OffsetDateTime.parse("2026-10-10T10:00:00+09:00");
    private static final Map<String, String> NICKNAMES = Map.of(ADMIN, "OpsUser", MEMBER, "YOUR_USERNAME");

    @Mock
    private BoardSearchRepository boardSearchRepository;

    @Mock
    private AccessControlService accessControlService;

    private BoardSearchService boardSearchService;

    @BeforeEach
    void setUp() {
        boardSearchService = new BoardSearchService(boardSearchRepository, accessControlService);
        when(accessControlService.isAdminEmail(ADMIN)).thenReturn(true);
        when(accessControlService.resolveDisplayNicknames(anyCollection())).thenAnswer(call -> {
            Map<String, String> found = new HashMap<>();
            call.<Collection<String>>getArgument(0).forEach(email -> found.put(email, NICKNAMES.get(email)));
            return found;
        });
    }

    private static FoundRow row(String kind, long id, String author, String voteStatus, long votes) {
        return new FoundRow(kind, id, "title " + id, "text " + id, author, WRITTEN, false, voteStatus, 7, 3, 2, votes);
    }

    @Test
    void findsNoticesAndFreeBoardPostsWithTheirWritersAndCounts() {
        when(boardSearchRepository.search(1L, "%rules%", false, BoardSearchService.PAGE_SIZE, 0)).thenReturn(List.of(
            row("NOTICE", 9, ADMIN, "OPEN", 11),
            row("FREE", 4, " Member@Hei.gg ", "NONE", 0),
            row("NOTICE", 3, ADMIN, "CLOSED", 0),
            // Its vote was taken off again: the votes cast are kept but not shown.
            row("NOTICE", 2, "gone@hei.gg", "NONE", 6)
        ));
        when(boardSearchRepository.count(1L, "%rules%", false)).thenReturn(4L);

        BoardSearchResponse response = boardSearchService.search(1L, " Member@Hei.gg ", "  rules  ", 1);

        assertThat(response.query()).isEqualTo("rules");
        assertThat(response.total()).isEqualTo(4L);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(BoardSearchService.PAGE_SIZE);
        assertThat(response.results())
            .extracting(
                BoardSearchItemResponse::kind, BoardSearchItemResponse::id, BoardSearchItemResponse::authorNickname,
                BoardSearchItemResponse::viewCount, BoardSearchItemResponse::commentCount,
                BoardSearchItemResponse::likeCount, BoardSearchItemResponse::voteCount
            )
            .containsExactly(
                tuple("NOTICE", 9L, "OpsUser", 7L, 3L, 2L, 11L),
                tuple("FREE", 4L, "YOUR_USERNAME", 7L, 3L, 2L, null),
                tuple("NOTICE", 3L, "OpsUser", 7L, 3L, 2L, 0L),
                tuple("NOTICE", 2L, null, 7L, 3L, 2L, null)
            );
    }

    @Test
    void looksIntoAdminOnlyNoticesForAdminsAlone() {
        boardSearchService.search(1L, MEMBER, "rules", 1);
        boardSearchService.search(1L, ADMIN, "rules", 1);

        verify(boardSearchRepository).search(1L, "%rules%", false, BoardSearchService.PAGE_SIZE, 0);
        verify(boardSearchRepository).count(1L, "%rules%", false);
        verify(boardSearchRepository).search(1L, "%rules%", true, BoardSearchService.PAGE_SIZE, 0);
        verify(boardSearchRepository).count(1L, "%rules%", true);
    }

    @Test
    void turnsThePageIntoAnOffsetAndNeverGoesBeforeTheFirst() {
        assertThat(boardSearchService.search(1L, MEMBER, "rules", 3).page()).isEqualTo(3);
        assertThat(boardSearchService.search(1L, MEMBER, "rules", 0).page()).isEqualTo(1);

        verify(boardSearchRepository).search(1L, "%rules%", false, BoardSearchService.PAGE_SIZE, 2 * BoardSearchService.PAGE_SIZE);
        verify(boardSearchRepository).search(1L, "%rules%", false, BoardSearchService.PAGE_SIZE, 0);
    }

    @Test
    void refusesAWordThatIsTooShortOrTooLong() {
        for (String query : new String[] {null, "", " a ", "x".repeat(BoardSearchService.MAX_QUERY_LENGTH + 1)}) {
            assertThatThrownBy(() -> boardSearchService.search(1L, MEMBER, query, 1))
                .isInstanceOf(IllegalArgumentException.class);
        }
        verify(boardSearchRepository, never()).search(any(), any(), anyBoolean(), anyInt(), anyInt());

        boardSearchService.search(1L, MEMBER, "x".repeat(BoardSearchService.MAX_QUERY_LENGTH), 1);
        boardSearchService.search(1L, MEMBER, "ab", 1);
        verify(boardSearchRepository).search(eq(1L), eq("%ab%"), eq(false), anyInt(), anyInt());
    }

    @Test
    void searchesForTheWordAsWrittenWildcardsIncluded() {
        boardSearchService.search(1L, MEMBER, "100%_a\\b", 1);

        verify(boardSearchRepository).search(1L, "%100\\%\\_a\\\\b%", false, BoardSearchService.PAGE_SIZE, 0);
        assertThat(BoardSearchService.escapeLike("plain")).isEqualTo("plain");
    }

    @Test
    void showsAShortTextWholeOnOneLine() {
        assertThat(BoardSearchService.snippet("  first line\n\n second   line ", "line")).isEqualTo("first line second line");
        assertThat(BoardSearchService.snippet(null, "line")).isEmpty();
        assertThat(BoardSearchService.snippet("   ", "line")).isEmpty();
    }

    @Test
    void cutsALongTextAroundTheFirstPlaceTheWordIsFound() {
        String text = "a".repeat(200) + " the RULES of the house " + "b".repeat(200);

        String snippet = BoardSearchService.snippet(text, "rules");

        assertThat(snippet).contains("the RULES of the house").startsWith("…").endsWith("…");
        assertThat(snippet.length()).isLessThanOrEqualTo(BoardSearchService.SNIPPET_LENGTH + 2);
    }

    @Test
    void startsALongTextFromItsBeginningWhenTheWordIsOnlyInTheTitle() {
        String text = "c".repeat(300);

        String snippet = BoardSearchService.snippet(text, "rules");

        assertThat(snippet).isEqualTo("c".repeat(BoardSearchService.SNIPPET_LENGTH) + "…");
    }

    @Test
    void endsALongTextWithoutTrailingDotsWhenTheWordIsNearItsEnd() {
        String text = "d".repeat(300) + " rules";

        String snippet = BoardSearchService.snippet(text, "rules");

        assertThat(snippet).startsWith("…").endsWith("rules");
        assertThat(snippet.length()).isEqualTo(BoardSearchService.SNIPPET_LENGTH + 1);
    }

    @Test
    void neverCutsThroughTheMiddleOfAnEmoji() {
        // Each emoji is two chars; 100 of them is 200 chars, cut off at an odd place without care.
        String text = "x" + "😀".repeat(100);

        String snippet = BoardSearchService.snippet(text, "rules");

        String body = snippet.substring(0, snippet.length() - 1);
        assertThat(snippet).endsWith("…");
        assertThat(Character.isHighSurrogate(body.charAt(body.length() - 1))).isFalse();
        assertThat(body.codePointCount(0, body.length())).isEqualTo(1 + (body.length() - 1) / 2);
    }
}
