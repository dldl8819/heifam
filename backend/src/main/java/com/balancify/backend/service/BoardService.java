package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.BoardCommentResponse;
import com.balancify.backend.api.group.dto.BoardPostDetailResponse;
import com.balancify.backend.api.group.dto.BoardPostListItemResponse;
import com.balancify.backend.api.group.dto.BoardPostListResponse;
import com.balancify.backend.repository.BoardRepository;
import com.balancify.backend.repository.BoardRepository.CommentRow;
import com.balancify.backend.repository.BoardRepository.PostRow;
import com.balancify.backend.service.exception.BoardForbiddenException;
import com.balancify.backend.service.exception.BoardLimitException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The member boards. Every member with access reads and writes the free board. The anonymous board
 * is a suggestion box: any member writes, and a post is read by the admins and by whoever wrote it.
 *
 * <p>On the anonymous board nobody is told who wrote a post, admins included: the answers carry no
 * name and no email for the writer, only "mine" for the writer themselves. The writer is kept in
 * the database so that they can find, change and remove their post and read the answers on it.
 */
@Service
public class BoardService {

    public enum Board {
        FREE,
        ANONYMOUS;

        /** The board named in a path ("free", "anonymous"); anything else is not a board. */
        public static Board fromPath(String value) {
            String name = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            for (Board board : values()) {
                if (board.name().equals(name)) {
                    return board;
                }
            }
            throw new NoSuchElementException("Board not found");
        }
    }

    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_CONTENT_LENGTH = 5000;
    static final int MAX_COMMENT_LENGTH = 500;
    static final int PAGE_SIZE = 20;
    // Posts one person may write on a board within a day.
    static final int FREE_POSTS_PER_DAY = 20;
    static final int ANONYMOUS_POSTS_PER_DAY = 5;

    private final BoardRepository boardRepository;
    private final AccessControlService accessControlService;

    public BoardService(BoardRepository boardRepository, AccessControlService accessControlService) {
        this.boardRepository = boardRepository;
        this.accessControlService = accessControlService;
    }

    /** The free board in full; of the anonymous board, everything for admins and their own posts for members. */
    @Transactional(readOnly = true)
    public BoardPostListResponse list(Long groupId, Board board, String email, int page) {
        String reader = normalizeEmail(email);
        boolean ownOnly = board == Board.ANONYMOUS && !accessControlService.isAdminEmail(reader);
        String author = ownOnly ? reader : null;
        int safePage = Math.max(1, page);
        List<PostRow> rows = boardRepository.listPosts(groupId, board.name(), author, PAGE_SIZE, (safePage - 1) * PAGE_SIZE);
        Map<String, String> nicknames = board == Board.FREE
            ? nicknames(rows.stream().map(PostRow::authorEmail).toList())
            : Map.of();
        List<BoardPostListItemResponse> posts = rows.stream()
            .map(row -> new BoardPostListItemResponse(
                row.id(),
                row.title(),
                nicknames.get(normalizeEmail(row.authorEmail())),
                row.createdAt(),
                row.commentCount(),
                row.likeCount(),
                row.viewCount(),
                reader.equals(normalizeEmail(row.authorEmail()))
            ))
            .toList();
        return new BoardPostListResponse(posts, boardRepository.countPosts(groupId, board.name(), author), safePage, PAGE_SIZE);
    }

    @Transactional
    public BoardPostDetailResponse create(Long groupId, Board board, String email, String title, String content) {
        String author = normalizeEmail(email);
        String cleanTitle = requireText(title, MAX_TITLE_LENGTH, "제목은 1~" + MAX_TITLE_LENGTH + "자로 입력해 주세요.");
        String cleanContent = requireText(content, MAX_CONTENT_LENGTH, "내용은 1~" + MAX_CONTENT_LENGTH + "자로 입력해 주세요.");
        int limit = board == Board.ANONYMOUS ? ANONYMOUS_POSTS_PER_DAY : FREE_POSTS_PER_DAY;
        if (boardRepository.countPostsSince(groupId, board.name(), author, OffsetDateTime.now().minusDays(1)) >= limit) {
            throw new BoardLimitException("하루에 올릴 수 있는 글 수를 넘었습니다. 내일 다시 올려 주세요.");
        }
        long postId = boardRepository.insertPost(groupId, board.name(), cleanTitle, cleanContent, author);
        return detail(requirePost(groupId, board, postId), board, author);
    }

    /** Opening a post counts its reader once among those who have seen it. */
    @Transactional
    public BoardPostDetailResponse open(Long groupId, Board board, Long postId, String email) {
        String reader = normalizeEmail(email);
        PostRow post = requireReadable(groupId, board, postId, reader);
        boardRepository.recordView(post.id(), reader);
        return detail(requirePost(groupId, board, postId), board, reader);
    }

    /** Only the writer changes a post; admins can remove one but not reword it. */
    @Transactional
    public BoardPostDetailResponse edit(Long groupId, Board board, Long postId, String email, String title, String content) {
        String actor = normalizeEmail(email);
        PostRow post = requireReadable(groupId, board, postId, actor);
        if (!actor.equals(normalizeEmail(post.authorEmail()))) {
            throw new BoardForbiddenException("본인 글만 수정할 수 있습니다.");
        }
        String cleanTitle = requireText(title, MAX_TITLE_LENGTH, "제목은 1~" + MAX_TITLE_LENGTH + "자로 입력해 주세요.");
        String cleanContent = requireText(content, MAX_CONTENT_LENGTH, "내용은 1~" + MAX_CONTENT_LENGTH + "자로 입력해 주세요.");
        if (!cleanTitle.equals(post.title()) || !cleanContent.equals(post.content())) {
            boardRepository.updatePost(post.id(), cleanTitle, cleanContent);
        }
        return detail(requirePost(groupId, board, postId), board, actor);
    }

    @Transactional
    public void delete(Long groupId, Board board, Long postId, String email) {
        String actor = normalizeEmail(email);
        PostRow post = requireReadable(groupId, board, postId, actor);
        if (!actor.equals(normalizeEmail(post.authorEmail())) && !accessControlService.isAdminEmail(actor)) {
            throw new BoardForbiddenException("본인 글만 지울 수 있습니다.");
        }
        boardRepository.deletePost(post.id());
    }

    /** Whoever may read a post may comment on it: on the anonymous board, the admins and the writer. */
    @Transactional
    public BoardPostDetailResponse addComment(Long groupId, Board board, Long postId, String email, String content) {
        String author = normalizeEmail(email);
        PostRow post = requireReadable(groupId, board, postId, author);
        String text = requireText(content, MAX_COMMENT_LENGTH, "댓글은 1~" + MAX_COMMENT_LENGTH + "자로 입력해 주세요.");
        boardRepository.insertComment(post.id(), author, text);
        return detail(requirePost(groupId, board, postId), board, author);
    }

    /** Writers remove their own comments; admins can remove any. */
    @Transactional
    public BoardPostDetailResponse deleteComment(Long groupId, Board board, Long postId, Long commentId, String email) {
        String actor = normalizeEmail(email);
        PostRow post = requireReadable(groupId, board, postId, actor);
        CommentRow comment = boardRepository.findComment(post.id(), commentId)
            .orElseThrow(() -> new NoSuchElementException("Comment not found"));
        if (!actor.equals(normalizeEmail(comment.authorEmail())) && !accessControlService.isAdminEmail(actor)) {
            throw new BoardForbiddenException("본인 댓글만 지울 수 있습니다.");
        }
        boardRepository.deleteComment(comment.id());
        return detail(requirePost(groupId, board, postId), board, actor);
    }

    /** Likes are for the free board; a post on the anonymous board has none. */
    @Transactional
    public BoardPostDetailResponse setLike(Long groupId, Board board, Long postId, String email, boolean liked) {
        if (board != Board.FREE) {
            throw new NoSuchElementException("Post not found");
        }
        String member = normalizeEmail(email);
        PostRow post = requireReadable(groupId, board, postId, member);
        if (liked) {
            boardRepository.like(post.id(), member);
        } else {
            boardRepository.unlike(post.id(), member);
        }
        return detail(requirePost(groupId, board, postId), board, member);
    }

    private BoardPostDetailResponse detail(PostRow post, Board board, String reader) {
        boolean admin = accessControlService.isAdminEmail(reader);
        String postAuthor = normalizeEmail(post.authorEmail());
        boolean mine = reader.equals(postAuthor);
        List<CommentRow> comments = boardRepository.listComments(post.id());

        // Whose names may be shown: everyone's on the free board. On the anonymous board only those
        // of the people answering; the writer of the post is never looked up, so cannot slip out.
        List<String> named = new ArrayList<>();
        if (board == Board.FREE) {
            named.add(postAuthor);
        }
        comments.forEach(comment -> {
            String commenter = normalizeEmail(comment.authorEmail());
            if (board == Board.FREE || !commenter.equals(postAuthor)) {
                named.add(commenter);
            }
        });
        Map<String, String> nicknames = nicknames(named);

        return new BoardPostDetailResponse(
            post.id(),
            board.name(),
            post.title(),
            post.content(),
            nicknames.get(board == Board.FREE ? postAuthor : ""),
            post.createdAt(),
            post.updatedAt() != null && post.createdAt() != null && post.updatedAt().isAfter(post.createdAt()),
            mine,
            mine,
            mine || admin,
            post.likeCount(),
            board == Board.FREE && boardRepository.hasLiked(post.id(), reader),
            post.viewCount(),
            comments.stream()
                .map(comment -> {
                    String commenter = normalizeEmail(comment.authorEmail());
                    boolean byPostAuthor = commenter.equals(postAuthor);
                    boolean myComment = reader.equals(commenter);
                    return new BoardCommentResponse(
                        comment.id(),
                        board == Board.ANONYMOUS && byPostAuthor ? null : nicknames.get(commenter),
                        byPostAuthor,
                        comment.content(),
                        comment.createdAt(),
                        myComment,
                        myComment || admin
                    );
                })
                .toList()
        );
    }

    private PostRow requirePost(Long groupId, Board board, Long postId) {
        return boardRepository.findPost(groupId, board.name(), postId)
            .orElseThrow(() -> new NoSuchElementException("Post not found"));
    }

    /** A post the reader may not see answers like one that is not there. */
    private PostRow requireReadable(Long groupId, Board board, Long postId, String reader) {
        PostRow post = requirePost(groupId, board, postId);
        if (board == Board.ANONYMOUS
            && !reader.equals(normalizeEmail(post.authorEmail()))
            && !accessControlService.isAdminEmail(reader)) {
            throw new NoSuchElementException("Post not found");
        }
        return post;
    }

    private Map<String, String> nicknames(List<String> emails) {
        Set<String> unique = new LinkedHashSet<>();
        emails.forEach(email -> {
            String normalized = normalizeEmail(email);
            if (!normalized.isEmpty()) {
                unique.add(normalized);
            }
        });
        Map<String, String> nicknames = new LinkedHashMap<>();
        if (!unique.isEmpty()) {
            accessControlService.resolveDisplayNicknames(unique).forEach((email, nickname) -> {
                if (nickname != null) {
                    nicknames.put(email, nickname);
                }
            });
        }
        return nicknames;
    }

    private static String requireText(String value, int maxLength, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
