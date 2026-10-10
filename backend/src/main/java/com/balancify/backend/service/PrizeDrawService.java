package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.PrizeDrawListResponse;
import com.balancify.backend.api.group.dto.PrizeDrawResponse;
import com.balancify.backend.api.group.dto.PrizeDrawSaveRequest;
import com.balancify.backend.api.group.dto.PrizeDrawWinnerRequest;
import com.balancify.backend.api.group.dto.PrizeDrawWinnerResponse;
import com.balancify.backend.repository.PrizeDrawRepository;
import com.balancify.backend.repository.PrizeDrawRepository.DrawRow;
import com.balancify.backend.repository.PrizeDrawRepository.WinnerRow;
import com.balancify.backend.service.exception.BoardForbiddenException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The records of prize draws. A draw is run in an admin's browser (the pinball page); what reaches
 * this service is its outcome, which an admin saves: the title, how many took part, and who won
 * which place and prize. Members read the records; a super admin can remove one.
 *
 * <p>The outcome is taken as the admin sends it: the balls ran where everyone could watch, and the
 * record says who saved it.
 */
@Service
public class PrizeDrawService {

    public static final String MODE_FIRST = "FIRST";
    public static final String MODE_LAST = "LAST";

    static final int MAX_TITLE_LENGTH = 100;
    static final int MIN_ENTRANTS = 2;
    static final int MAX_ENTRANTS = 100;
    static final int MAX_WINNERS = 10;
    static final int MAX_NAME_LENGTH = 50;
    static final int MAX_PRIZE_LENGTH = 100;
    static final int LIST_LIMIT = 50;

    private final PrizeDrawRepository prizeDrawRepository;
    private final AccessControlService accessControlService;
    private final OperationAuditLogService operationAuditLogService;

    public PrizeDrawService(
        PrizeDrawRepository prizeDrawRepository,
        AccessControlService accessControlService,
        OperationAuditLogService operationAuditLogService
    ) {
        this.prizeDrawRepository = prizeDrawRepository;
        this.accessControlService = accessControlService;
        this.operationAuditLogService = operationAuditLogService;
    }

    @Transactional(readOnly = true)
    public PrizeDrawListResponse list(Long groupId, String email) {
        String reader = normalizeEmail(email);
        boolean superAdmin = accessControlService.isSuperAdminEmail(reader);
        List<DrawRow> draws = prizeDrawRepository.listDraws(groupId, LIST_LIMIT);

        Map<Long, List<PrizeDrawWinnerResponse>> winners = new HashMap<>();
        for (WinnerRow winner : prizeDrawRepository.listWinners(draws.stream().map(DrawRow::id).toList())) {
            winners.computeIfAbsent(winner.drawId(), id -> new ArrayList<>())
                .add(new PrizeDrawWinnerResponse(winner.place(), winner.name(), winner.prize()));
        }
        Set<String> savers = new LinkedHashSet<>();
        draws.forEach(draw -> {
            String saver = normalizeEmail(draw.createdByEmail());
            if (!saver.isEmpty()) {
                savers.add(saver);
            }
        });
        Map<String, String> nicknames = savers.isEmpty() ? Map.of() : accessControlService.resolveDisplayNicknames(savers);

        return new PrizeDrawListResponse(
            draws.stream()
                .map(draw -> new PrizeDrawResponse(
                    draw.id(),
                    draw.title(),
                    draw.mode(),
                    draw.entrantCount(),
                    draw.createdAt(),
                    nicknames.get(normalizeEmail(draw.createdByEmail())),
                    superAdmin,
                    winners.getOrDefault(draw.id(), List.of())
                ))
                .toList(),
            accessControlService.isAdminEmail(reader)
        );
    }

    @Transactional
    public PrizeDrawListResponse save(Long groupId, String email, String actorNickname, PrizeDrawSaveRequest request) {
        String actor = normalizeEmail(email);
        if (!accessControlService.isAdminEmail(actor)) {
            throw new BoardForbiddenException("운영진만 추첨 결과를 저장할 수 있습니다.");
        }
        if (request == null) {
            throw new IllegalArgumentException("추첨 결과가 비어 있습니다.");
        }
        String title = requireText(request.title(), MAX_TITLE_LENGTH, "추첨 제목은 1~" + MAX_TITLE_LENGTH + "자로 입력해 주세요.");
        String mode = request.mode() == null ? "" : request.mode().trim().toUpperCase(Locale.ROOT);
        if (!MODE_FIRST.equals(mode) && !MODE_LAST.equals(mode)) {
            throw new IllegalArgumentException("당첨 방식은 FIRST 또는 LAST여야 합니다.");
        }
        int entrantCount = request.entrantCount() == null ? 0 : request.entrantCount();
        if (entrantCount < MIN_ENTRANTS || entrantCount > MAX_ENTRANTS) {
            throw new IllegalArgumentException("참가자는 " + MIN_ENTRANTS + "~" + MAX_ENTRANTS + "명이어야 합니다.");
        }
        List<PrizeDrawWinnerRequest> winners = request.winners() == null ? List.of() : request.winners();
        if (winners.isEmpty() || winners.size() > MAX_WINNERS || winners.size() > entrantCount) {
            throw new IllegalArgumentException("당첨자는 1~" + MAX_WINNERS + "명이고 참가자보다 많을 수 없습니다.");
        }

        // Places are 1, 2, 3 ... with none missing or twice; a player wins one place at most.
        Set<Integer> places = new HashSet<>();
        Set<Long> playerIds = new HashSet<>();
        List<CleanWinner> clean = new ArrayList<>();
        for (PrizeDrawWinnerRequest winner : winners) {
            if (winner == null || winner.place() == null || winner.place() < 1 || winner.place() > winners.size()
                || !places.add(winner.place())) {
                throw new IllegalArgumentException("당첨 순위가 올바르지 않습니다.");
            }
            String name = requireText(winner.name(), MAX_NAME_LENGTH, "당첨자 이름은 1~" + MAX_NAME_LENGTH + "자여야 합니다.");
            String prize = winner.prize() == null ? "" : winner.prize().trim();
            if (prize.length() > MAX_PRIZE_LENGTH) {
                throw new IllegalArgumentException("상품명은 " + MAX_PRIZE_LENGTH + "자 이하로 입력해 주세요.");
            }
            if (winner.playerId() != null && !playerIds.add(winner.playerId())) {
                throw new IllegalArgumentException("같은 선수가 두 번 당첨될 수 없습니다.");
            }
            clean.add(new CleanWinner(winner.place(), name, winner.playerId(), prize.isEmpty() ? null : prize));
        }
        if (!playerIds.isEmpty() && prizeDrawRepository.countGroupPlayers(groupId, playerIds) != playerIds.size()) {
            throw new IllegalArgumentException("명단에 없는 선수가 당첨자에 있습니다.");
        }

        long drawId = prizeDrawRepository.insertDraw(groupId, title, mode, entrantCount, actor);
        clean.stream()
            .sorted((left, right) -> Integer.compare(left.place(), right.place()))
            .forEach(winner -> prizeDrawRepository.insertWinner(drawId, winner.place(), winner.name(), winner.playerId(), winner.prize()));
        operationAuditLogService.recordPrizeDraw(
            OperationAuditLogService.ACTION_PRIZE_DRAW_SAVED, actor, actorNickname, drawId, groupId, title,
            "entrants=" + entrantCount + ", winners=" + clean.size() + ", mode=" + mode
        );
        return list(groupId, actor);
    }

    /** Removing a record is for super admins, as with notices: a draw that was saved stays said. */
    @Transactional
    public PrizeDrawListResponse delete(Long groupId, Long drawId, String email, String actorNickname) {
        String actor = normalizeEmail(email);
        if (!accessControlService.isSuperAdminEmail(actor)) {
            throw new BoardForbiddenException("최고관리자만 추첨 기록을 삭제할 수 있습니다.");
        }
        DrawRow draw = prizeDrawRepository.findDraw(groupId, drawId)
            .orElseThrow(() -> new NoSuchElementException("Draw not found"));
        prizeDrawRepository.deleteDraw(draw.id());
        operationAuditLogService.recordPrizeDraw(
            OperationAuditLogService.ACTION_PRIZE_DRAW_DELETED, actor, actorNickname, draw.id(), groupId, draw.title(), null
        );
        return list(groupId, actor);
    }

    private record CleanWinner(int place, String name, Long playerId, String prize) {
    }

    private static String requireText(String value, int maxLength, String message) {
        String text = Objects.toString(value, "").trim();
        if (text.isEmpty() || text.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
