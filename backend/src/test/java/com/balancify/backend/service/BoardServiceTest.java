package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.BoardCommentResponse;
import com.balancify.backend.api.group.dto.BoardPostDetailResponse;
import com.balancify.backend.api.group.dto.BoardPostListItemResponse;
import com.balancify.backend.api.group.dto.BoardPostListResponse;
import com.balancify.backend.repository.BoardRepository;
import com.balancify.backend.repository.BoardRepository.CommentRow;
import com.balancify.backend.repository.BoardRepository.PostRow;
import com.balancify.backend.service.BoardService.Board;
import com.balancify.backend.service.exception.BoardForbiddenException;
import com.balancify.backend.service.exception.BoardLimitException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoardServiceTest {

    private static final String ADMIN = "ops@hei.gg";
    private static final String WRITER = "member@hei.gg";
    private static final String OTHER = "other@hei.gg";
    private static final OffsetDateTime WRITTEN = OffsetDateTime.parse("2026-10-10T10:00:00+09:00");
    private static final Map<String, String> NICKNAMES = Map.of(ADMIN, "OpsUser", WRITER, "YOUR_USERNAME", OTHER, "OtherUser");

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private AccessControlService accessControlService;

    private BoardService boardService;

    @BeforeEach
    void setUp() {
        boardService = new BoardService(boardRepository, accessControlService);
        when(accessControlService.isAdminEmail(ADMIN)).thenReturn(true);
        // Only the nicknames asked for come back, so a test sees whose names the service looked up.
        when(accessControlService.resolveDisplayNicknames(anyCollection())).thenAnswer(invocation -> {
            Collection<String> emails = invocation.getArgument(0);
            Map<String, String> found = new HashMap<>();
            emails.forEach(email -> {
                if (NICKNAMES.containsKey(email)) {
                    found.put(email, NICKNAMES.get(email));
                }
            });
            return found;
        });
    }

    @Test
    void readsTheBoardNamedInThePath() {
        assertThat(Board.fromPath("free")).isEqualTo(Board.FREE);
        assertThat(Board.fromPath(" Anonymous ")).isEqualTo(Board.ANONYMOUS);
        for (String path : new String[] {null, "", "notices", "FREE2"}) {
            assertThatThrownBy(() -> Board.fromPath(path)).isInstanceOf(NoSuchElementException.class);
        }
    }

    @Test
    void listsTheWholeFreeBoardWithItsWriters() {
        when(boardRepository.listPosts(1L, "FREE", null, 20, 0)).thenReturn(List.of(post(2L, "FREE", OTHER), post(1L, "FREE", WRITER)));
        when(boardRepository.countPosts(1L, "FREE", null)).thenReturn(2L);

        BoardPostListResponse list = boardService.list(1L, Board.FREE, WRITER, 1);

        assertThat(list.total()).isEqualTo(2);
        assertThat(list.posts())
            .extracting(BoardPostListItemResponse::id, BoardPostListItemResponse::authorNickname, BoardPostListItemResponse::mine)
            .containsExactly(tuple(2L, "OtherUser", false), tuple(1L, "YOUR_USERNAME", true));
    }

    @Test
    void asksForTheRightPageAndNeverOneBeforeTheFirst() {
        boardService.list(1L, Board.FREE, WRITER, 3);
        boardService.list(1L, Board.FREE, WRITER, 0);
        boardService.list(1L, Board.FREE, WRITER, -5);

        verify(boardRepository).listPosts(1L, "FREE", null, 20, 40);
        verify(boardRepository, org.mockito.Mockito.times(2)).listPosts(1L, "FREE", null, 20, 0);
    }

    @Test
    void showsAMemberOnlyTheirOwnAnonymousPostsAndAdminsAllOfThem() {
        when(boardRepository.listPosts(1L, "ANONYMOUS", WRITER, 20, 0)).thenReturn(List.of(post(1L, "ANONYMOUS", WRITER)));
        when(boardRepository.listPosts(eq(1L), eq("ANONYMOUS"), isNull(), eq(20), eq(0)))
            .thenReturn(List.of(post(2L, "ANONYMOUS", OTHER), post(1L, "ANONYMOUS", WRITER)));

        BoardPostListResponse forWriter = boardService.list(1L, Board.ANONYMOUS, WRITER, 1);
        BoardPostListResponse forAdmin = boardService.list(1L, Board.ANONYMOUS, ADMIN, 1);

        assertThat(forWriter.posts()).extracting(BoardPostListItemResponse::id, BoardPostListItemResponse::mine)
            .containsExactly(tuple(1L, true));
        assertThat(forAdmin.posts()).extracting(BoardPostListItemResponse::id, BoardPostListItemResponse::mine)
            .containsExactly(tuple(2L, false), tuple(1L, false));
        // Nobody is named on the anonymous board, and nobody's name is even looked up.
        assertThat(forAdmin.posts()).extracting(BoardPostListItemResponse::authorNickname).containsOnlyNulls();
        assertThat(forWriter.posts()).extracting(BoardPostListItemResponse::authorNickname).containsOnlyNulls();
        verify(accessControlService, never()).resolveDisplayNicknames(anyCollection());
    }

    @Test
    void savesATrimmedPostAndRefusesOneWithoutTitleOrText() {
        when(boardRepository.insertPost(1L, "FREE", "title", "text", WRITER)).thenReturn(7L);
        when(boardRepository.findPost(1L, "FREE", 7L)).thenReturn(Optional.of(post(7L, "FREE", WRITER)));

        BoardPostDetailResponse created = boardService.create(1L, Board.FREE, " Member@Hei.gg ", "  title ", " text  ");

        assertThat(created.id()).isEqualTo(7L);
        assertThat(created.mine()).isTrue();
        verify(boardRepository).insertPost(1L, "FREE", "title", "text", WRITER);
        for (String[] bad : new String[][] {{" ", "text"}, {"title", " "}, {null, "text"}, {"x".repeat(201), "text"}, {"title", "x".repeat(5001)}}) {
            assertThatThrownBy(() -> boardService.create(1L, Board.FREE, WRITER, bad[0], bad[1]))
                .isInstanceOf(IllegalArgumentException.class);
        }
        verify(boardRepository, org.mockito.Mockito.times(1)).insertPost(any(), any(), any(), any(), any());
    }

    @Test
    void stopsAPersonAtTheDaysLimitOfPostsWhichIsLowerOnTheAnonymousBoard() {
        when(boardRepository.countPostsSince(eq(1L), eq("FREE"), eq(WRITER), any())).thenReturn(20L);
        when(boardRepository.countPostsSince(eq(1L), eq("ANONYMOUS"), eq(WRITER), any())).thenReturn(5L);
        when(boardRepository.countPostsSince(eq(1L), eq("ANONYMOUS"), eq(OTHER), any())).thenReturn(4L);
        when(boardRepository.insertPost(1L, "ANONYMOUS", "title", "text", OTHER)).thenReturn(9L);
        when(boardRepository.findPost(1L, "ANONYMOUS", 9L)).thenReturn(Optional.of(post(9L, "ANONYMOUS", OTHER)));

        assertThatThrownBy(() -> boardService.create(1L, Board.FREE, WRITER, "title", "text")).isInstanceOf(BoardLimitException.class);
        assertThatThrownBy(() -> boardService.create(1L, Board.ANONYMOUS, WRITER, "title", "text")).isInstanceOf(BoardLimitException.class);
        assertThat(boardService.create(1L, Board.ANONYMOUS, OTHER, "title", "text").id()).isEqualTo(9L);

        ArgumentCaptor<OffsetDateTime> since = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(boardRepository).countPostsSince(eq(1L), eq("FREE"), eq(WRITER), since.capture());
        assertThat(since.getValue()).isBetween(OffsetDateTime.now().minusHours(25), OffsetDateTime.now().minusHours(23));
    }

    @Test
    void countsWhoeverOpensAFreePostAndNamesItsWriterAndCommenters() {
        PostRow post = post(7L, "FREE", WRITER);
        when(boardRepository.findPost(1L, "FREE", 7L)).thenReturn(Optional.of(post));
        when(boardRepository.listComments(7L)).thenReturn(List.of(comment(31L, 7L, OTHER), comment(32L, 7L, WRITER)));
        when(boardRepository.hasLiked(7L, OTHER)).thenReturn(true);

        BoardPostDetailResponse detail = boardService.open(1L, Board.FREE, 7L, OTHER);

        verify(boardRepository).recordView(7L, OTHER);
        assertThat(detail.board()).isEqualTo("FREE");
        assertThat(detail.authorNickname()).isEqualTo("YOUR_USERNAME");
        assertThat(List.of(detail.mine(), detail.canEdit(), detail.canDelete(), detail.likedByMe()))
            .containsExactly(false, false, false, true);
        assertThat(detail.comments())
            .extracting(BoardCommentResponse::id, BoardCommentResponse::authorNickname, BoardCommentResponse::byPostAuthor,
                BoardCommentResponse::mine, BoardCommentResponse::canDelete)
            .containsExactly(tuple(31L, "OtherUser", false, true, true), tuple(32L, "YOUR_USERNAME", true, false, false));
    }

    @Test
    void showsAnAnonymousPostToAdminsAndItsWriterAndToNobodyElse() {
        when(boardRepository.findPost(1L, "ANONYMOUS", 7L)).thenReturn(Optional.of(post(7L, "ANONYMOUS", WRITER)));

        assertThat(boardService.open(1L, Board.ANONYMOUS, 7L, ADMIN).id()).isEqualTo(7L);
        assertThat(boardService.open(1L, Board.ANONYMOUS, 7L, WRITER).mine()).isTrue();
        assertThatThrownBy(() -> boardService.open(1L, Board.ANONYMOUS, 7L, OTHER)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> boardService.addComment(1L, Board.ANONYMOUS, 7L, OTHER, "hello"))
            .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> boardService.edit(1L, Board.ANONYMOUS, 7L, OTHER, "t", "c")).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> boardService.delete(1L, Board.ANONYMOUS, 7L, OTHER)).isInstanceOf(NoSuchElementException.class);

        verify(boardRepository, never()).recordView(7L, OTHER);
        verify(boardRepository, never()).insertComment(anyLong(), any(), any());
        verify(boardRepository, never()).deletePost(anyLong());
    }

    @Test
    void tellsNobodyWhoWroteAnAnonymousPostNotEvenAnAdmin() {
        when(boardRepository.findPost(1L, "ANONYMOUS", 7L)).thenReturn(Optional.of(post(7L, "ANONYMOUS", WRITER)));
        when(boardRepository.listComments(7L)).thenReturn(List.of(comment(31L, 7L, ADMIN), comment(32L, 7L, WRITER)));

        BoardPostDetailResponse forAdmin = boardService.open(1L, Board.ANONYMOUS, 7L, ADMIN);
        BoardPostDetailResponse forWriter = boardService.open(1L, Board.ANONYMOUS, 7L, WRITER);

        assertThat(forAdmin.authorNickname()).isNull();
        assertThat(forAdmin.mine()).isFalse();
        // The admin who answers is named; the writer's own comment says only that it is the writer's.
        assertThat(forAdmin.comments())
            .extracting(BoardCommentResponse::authorNickname, BoardCommentResponse::byPostAuthor, BoardCommentResponse::mine)
            .containsExactly(tuple("OpsUser", false, true), tuple(null, true, false));
        assertThat(forWriter.authorNickname()).isNull();
        assertThat(forWriter.comments())
            .extracting(BoardCommentResponse::authorNickname, BoardCommentResponse::byPostAuthor, BoardCommentResponse::mine)
            .containsExactly(tuple("OpsUser", false, false), tuple(null, true, true));
        assertThat(forAdmin.toString()).doesNotContain(WRITER).doesNotContain("YOUR_USERNAME");

        // The writer's nickname is never asked for, so it cannot slip into an answer.
        ArgumentCaptor<Collection<String>> asked = ArgumentCaptor.forClass(Collection.class);
        verify(accessControlService, org.mockito.Mockito.atLeastOnce()).resolveDisplayNicknames(asked.capture());
        assertThat(asked.getAllValues()).allSatisfy(emails -> assertThat(emails).doesNotContain(WRITER));
    }

    @Test
    void letsOnlyTheWriterEditAPost() {
        PostRow post = post(7L, "FREE", WRITER);
        when(boardRepository.findPost(1L, "FREE", 7L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> boardService.edit(1L, Board.FREE, 7L, ADMIN, "new", "text")).isInstanceOf(BoardForbiddenException.class);
        assertThatThrownBy(() -> boardService.edit(1L, Board.FREE, 7L, OTHER, "new", "text")).isInstanceOf(BoardForbiddenException.class);
        assertThatThrownBy(() -> boardService.edit(1L, Board.FREE, 7L, WRITER, " ", "text")).isInstanceOf(IllegalArgumentException.class);
        verify(boardRepository, never()).updatePost(anyLong(), any(), any());

        // Saved as it was, nothing is written, so the post is not marked as edited.
        boardService.edit(1L, Board.FREE, 7L, WRITER, " title-7 ", "content-7");
        verify(boardRepository, never()).updatePost(anyLong(), any(), any());
        boardService.edit(1L, Board.FREE, 7L, WRITER, "new title", " new text ");
        verify(boardRepository).updatePost(7L, "new title", "new text");
    }

    @Test
    void marksAPostChangedAfterItWasWritten() {
        PostRow edited = new PostRow(7L, "FREE", "t", "c", WRITER, WRITTEN, WRITTEN.plusMinutes(5), 0, 0, 0);
        when(boardRepository.findPost(1L, "FREE", 7L)).thenReturn(Optional.of(edited));
        when(boardRepository.findPost(1L, "FREE", 8L)).thenReturn(Optional.of(post(8L, "FREE", WRITER)));

        assertThat(boardService.open(1L, Board.FREE, 7L, OTHER).edited()).isTrue();
        assertThat(boardService.open(1L, Board.FREE, 8L, OTHER).edited()).isFalse();
    }

    @Test
    void letsTheWriterAndAdminsRemoveAPost() {
        when(boardRepository.findPost(1L, "FREE", 7L)).thenReturn(Optional.of(post(7L, "FREE", WRITER)));
        when(boardRepository.findPost(1L, "ANONYMOUS", 8L)).thenReturn(Optional.of(post(8L, "ANONYMOUS", WRITER)));

        assertThatThrownBy(() -> boardService.delete(1L, Board.FREE, 7L, OTHER)).isInstanceOf(BoardForbiddenException.class);
        verify(boardRepository, never()).deletePost(anyLong());

        boardService.delete(1L, Board.FREE, 7L, WRITER);
        boardService.delete(1L, Board.ANONYMOUS, 8L, ADMIN);
        verify(boardRepository).deletePost(7L);
        verify(boardRepository).deletePost(8L);
    }

    @Test
    void savesCommentsWithinTheLimitAndLetsWritersAndAdminsRemoveThem() {
        when(boardRepository.findPost(1L, "FREE", 7L)).thenReturn(Optional.of(post(7L, "FREE", WRITER)));
        when(boardRepository.findComment(7L, 31L)).thenReturn(Optional.of(comment(31L, 7L, OTHER)));
        when(boardRepository.findComment(7L, 99L)).thenReturn(Optional.empty());

        boardService.addComment(1L, Board.FREE, 7L, OTHER, "  hello ");
        verify(boardRepository).insertComment(7L, OTHER, "hello");
        assertThatThrownBy(() -> boardService.addComment(1L, Board.FREE, 7L, OTHER, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> boardService.addComment(1L, Board.FREE, 7L, OTHER, "x".repeat(501)))
            .isInstanceOf(IllegalArgumentException.class);

        // Not even the writer of the post removes someone else's comment.
        assertThatThrownBy(() -> boardService.deleteComment(1L, Board.FREE, 7L, 31L, WRITER)).isInstanceOf(BoardForbiddenException.class);
        assertThatThrownBy(() -> boardService.deleteComment(1L, Board.FREE, 7L, 99L, ADMIN)).isInstanceOf(NoSuchElementException.class);
        verify(boardRepository, never()).deleteComment(anyLong());
        boardService.deleteComment(1L, Board.FREE, 7L, 31L, OTHER);
        boardService.deleteComment(1L, Board.FREE, 7L, 31L, ADMIN);
        verify(boardRepository, org.mockito.Mockito.times(2)).deleteComment(31L);
    }

    @Test
    void likesAFreePostAndHasNoLikesOnTheAnonymousBoard() {
        when(boardRepository.findPost(1L, "FREE", 7L)).thenReturn(Optional.of(post(7L, "FREE", WRITER)));
        when(boardRepository.findPost(1L, "ANONYMOUS", 8L)).thenReturn(Optional.of(post(8L, "ANONYMOUS", WRITER)));

        boardService.setLike(1L, Board.FREE, 7L, OTHER, true);
        boardService.setLike(1L, Board.FREE, 7L, OTHER, false);
        verify(boardRepository).like(7L, OTHER);
        verify(boardRepository).unlike(7L, OTHER);

        assertThatThrownBy(() -> boardService.setLike(1L, Board.ANONYMOUS, 8L, ADMIN, true)).isInstanceOf(NoSuchElementException.class);
        verify(boardRepository, never()).like(eq(8L), any());
        verify(boardRepository, never()).hasLiked(eq(8L), any());
    }

    @Test
    void answersNotFoundForAPostThatIsNotOnTheBoard() {
        when(boardRepository.findPost(anyLong(), any(), anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> boardService.open(1L, Board.FREE, 404L, WRITER)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> boardService.addComment(1L, Board.FREE, 404L, WRITER, "hello")).isInstanceOf(NoSuchElementException.class);
        verify(boardRepository, never()).recordView(anyLong(), any());
        verify(boardRepository, never()).listPosts(anyLong(), any(), any(), anyInt(), anyInt());
    }

    private static PostRow post(Long id, String board, String author) {
        return new PostRow(id, board, "title-" + id, "content-" + id, author, WRITTEN, WRITTEN, 0, 0, 0);
    }

    private static CommentRow comment(Long id, Long postId, String author) {
        return new CommentRow(id, postId, author, "comment-" + id, WRITTEN);
    }
}
