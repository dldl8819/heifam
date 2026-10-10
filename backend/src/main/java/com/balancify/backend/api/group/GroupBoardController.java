package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.BoardCommentRequest;
import com.balancify.backend.api.group.dto.BoardPostDetailResponse;
import com.balancify.backend.api.group.dto.BoardPostListResponse;
import com.balancify.backend.api.group.dto.BoardPostRequest;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.BoardService;
import com.balancify.backend.service.BoardService.Board;
import com.balancify.backend.service.exception.BoardForbiddenException;
import com.balancify.backend.service.exception.BoardLimitException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The member boards: {board} is "free", "anonymous" or "video". Every route needs member access
 * (ServiceAccessFilter); who may read or change which post is BoardService's to decide.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/boards/{board}/posts")
public class GroupBoardController {

    private final BoardService boardService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public GroupBoardController(BoardService boardService, AuthenticatedRequestResolver authenticatedRequestResolver) {
        this.boardService = boardService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    @GetMapping
    public BoardPostListResponse list(
        @PathVariable Long groupId,
        @PathVariable String board,
        @RequestParam(defaultValue = "1") int page,
        HttpServletRequest request
    ) {
        String email = requireRequestEmail(request);
        return handle(() -> boardService.list(groupId, Board.fromPath(board), email, page));
    }

    @PostMapping
    public BoardPostDetailResponse create(
        @PathVariable Long groupId,
        @PathVariable String board,
        @RequestBody BoardPostRequest body,
        HttpServletRequest request
    ) {
        String email = requireRequestEmail(request);
        return handle(() -> boardService.create(
            groupId,
            Board.fromPath(board),
            email,
            body == null ? null : body.title(),
            body == null ? null : body.content(),
            body == null ? null : body.videoUrl()
        ));
    }

    @GetMapping("/{postId}")
    public BoardPostDetailResponse open(
        @PathVariable Long groupId,
        @PathVariable String board,
        @PathVariable Long postId,
        HttpServletRequest request
    ) {
        String email = requireRequestEmail(request);
        return handle(() -> boardService.open(groupId, Board.fromPath(board), postId, email));
    }

    @PutMapping("/{postId}")
    public BoardPostDetailResponse edit(
        @PathVariable Long groupId,
        @PathVariable String board,
        @PathVariable Long postId,
        @RequestBody BoardPostRequest body,
        HttpServletRequest request
    ) {
        String email = requireRequestEmail(request);
        return handle(() -> boardService.edit(
            groupId,
            Board.fromPath(board),
            postId,
            email,
            body == null ? null : body.title(),
            body == null ? null : body.content(),
            body == null ? null : body.videoUrl()
        ));
    }

    @DeleteMapping("/{postId}")
    public void delete(
        @PathVariable Long groupId,
        @PathVariable String board,
        @PathVariable Long postId,
        HttpServletRequest request
    ) {
        String email = requireRequestEmail(request);
        handle(() -> {
            boardService.delete(groupId, Board.fromPath(board), postId, email);
            return null;
        });
    }

    @PostMapping("/{postId}/comments")
    public BoardPostDetailResponse addComment(
        @PathVariable Long groupId,
        @PathVariable String board,
        @PathVariable Long postId,
        @RequestBody BoardCommentRequest body,
        HttpServletRequest request
    ) {
        String email = requireRequestEmail(request);
        return handle(() -> boardService.addComment(
            groupId,
            Board.fromPath(board),
            postId,
            email,
            body == null ? null : body.content()
        ));
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    public BoardPostDetailResponse deleteComment(
        @PathVariable Long groupId,
        @PathVariable String board,
        @PathVariable Long postId,
        @PathVariable Long commentId,
        HttpServletRequest request
    ) {
        String email = requireRequestEmail(request);
        return handle(() -> boardService.deleteComment(groupId, Board.fromPath(board), postId, commentId, email));
    }

    @PutMapping("/{postId}/like")
    public BoardPostDetailResponse like(
        @PathVariable Long groupId,
        @PathVariable String board,
        @PathVariable Long postId,
        HttpServletRequest request
    ) {
        String email = requireRequestEmail(request);
        return handle(() -> boardService.setLike(groupId, Board.fromPath(board), postId, email, true));
    }

    @DeleteMapping("/{postId}/like")
    public BoardPostDetailResponse unlike(
        @PathVariable Long groupId,
        @PathVariable String board,
        @PathVariable Long postId,
        HttpServletRequest request
    ) {
        String email = requireRequestEmail(request);
        return handle(() -> boardService.setLike(groupId, Board.fromPath(board), postId, email, false));
    }

    private String requireRequestEmail(HttpServletRequest request) {
        String requestEmail = authenticatedRequestResolver.resolve(request).email();
        if (requestEmail.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid Supabase bearer token is required");
        }
        return requestEmail;
    }

    private <T> T handle(Supplier<T> action) {
        try {
            return action.get();
        } catch (BoardForbiddenException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (BoardLimitException exception) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), exception);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
