package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.BoardSearchItemResponse;
import com.balancify.backend.api.group.dto.BoardSearchResponse;
import com.balancify.backend.repository.BoardSearchRepository;
import com.balancify.backend.repository.BoardSearchRepository.FoundRow;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Search across what a member may read anyway: the notices open to them, the free board and the
 * video board. A notice kept to admins is found only by admins. The anonymous board and the
 * nickname requests are not searched at all.
 */
@Service
public class BoardSearchService {

    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 50;
    static final int PAGE_SIZE = 20;
    static final int SNIPPET_LENGTH = 120;
    // How much of a snippet comes before the word found, so the match is read in its sentence.
    private static final int SNIPPET_LEAD = 30;

    private final BoardSearchRepository boardSearchRepository;
    private final AccessControlService accessControlService;

    public BoardSearchService(BoardSearchRepository boardSearchRepository, AccessControlService accessControlService) {
        this.boardSearchRepository = boardSearchRepository;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public BoardSearchResponse search(Long groupId, String email, String query, int page) {
        String word = query == null ? "" : query.trim();
        if (word.length() < MIN_QUERY_LENGTH || word.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException(
                "검색어는 " + MIN_QUERY_LENGTH + "~" + MAX_QUERY_LENGTH + "자로 입력해 주세요."
            );
        }
        boolean admin = accessControlService.isAdminEmail(normalizeEmail(email));
        String pattern = "%" + escapeLike(word) + "%";
        int safePage = Math.max(1, page);
        List<FoundRow> rows = boardSearchRepository.search(groupId, pattern, admin, PAGE_SIZE, (safePage - 1) * PAGE_SIZE);

        Set<String> authors = new LinkedHashSet<>();
        rows.forEach(row -> {
            String author = normalizeEmail(row.authorEmail());
            if (!author.isEmpty()) {
                authors.add(author);
            }
        });
        Map<String, String> nicknames = authors.isEmpty()
            ? Map.of()
            : accessControlService.resolveDisplayNicknames(authors);

        List<BoardSearchItemResponse> results = rows.stream()
            .map(row -> new BoardSearchItemResponse(
                row.kind(),
                row.id(),
                row.title(),
                snippet(row.content(), word),
                nicknames.get(normalizeEmail(row.authorEmail())),
                row.createdAt(),
                row.adminOnly(),
                row.viewCount(),
                row.commentCount(),
                row.likeCount(),
                NoticeVotes.OPEN.equals(row.voteStatus()) || NoticeVotes.CLOSED.equals(row.voteStatus())
                    ? Long.valueOf(row.voteCount())
                    : null
            ))
            .toList();
        return new BoardSearchResponse(word, results, boardSearchRepository.count(groupId, pattern, admin), safePage, PAGE_SIZE);
    }

    /** The word as a LIKE pattern matches it letter for letter: its own % and _ are no wildcards. */
    static String escapeLike(String word) {
        return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * A stretch of the text on one line: the whole of a short text, otherwise the part around the
     * first place the word is found, or the beginning when the word is only in the title.
     */
    static String snippet(String content, String word) {
        String text = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (text.length() <= SNIPPET_LENGTH) {
            return text;
        }
        int foundAt = indexOfIgnoreCase(text, word);
        int start = foundAt < 0 ? 0 : Math.max(0, foundAt - SNIPPET_LEAD);
        int end = Math.min(text.length(), start + SNIPPET_LENGTH);
        start = Math.max(0, end - SNIPPET_LENGTH);
        // Never through the middle of a character written as a pair (an emoji).
        if (start > 0 && Character.isLowSurrogate(text.charAt(start))) {
            start++;
        }
        if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return (start > 0 ? "…" : "") + text.substring(start, end).trim() + (end < text.length() ? "…" : "");
    }

    private static int indexOfIgnoreCase(String text, String word) {
        for (int index = 0; index + word.length() <= text.length(); index++) {
            if (text.regionMatches(true, index, word, 0, word.length())) {
                return index;
            }
        }
        return -1;
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
