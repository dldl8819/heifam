package com.balancify.backend.service;

import com.balancify.backend.api.match.dto.MatchImportFailedRowResponse;
import com.balancify.backend.api.match.dto.MatchImportResponse;
import com.balancify.backend.api.match.dto.MatchImportRowRequest;
import com.balancify.backend.api.match.dto.MatchResultRequest;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchImportService {

    private static final String TEAM_HOME = "HOME";
    private static final String TEAM_AWAY = "AWAY";
    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(9);

    private final GroupRepository groupRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final MatchResultService matchResultService;

    public MatchImportService(
        GroupRepository groupRepository,
        PlayerRepository playerRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        MatchResultService matchResultService
    ) {
        this.groupRepository = groupRepository;
        this.playerRepository = playerRepository;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.matchResultService = matchResultService;
    }

    @Transactional
    public MatchImportResponse importMatches(List<MatchImportRowRequest> requestRows) {
        List<MatchImportRowRequest> rows = requestRows == null ? List.of() : requestRows;
        List<MatchImportFailedRowResponse> failedRows = new ArrayList<>();
        int importedCount = 0;

        Map<Long, Group> groupCache = new HashMap<>();
        Map<Long, Map<String, List<Player>>> playersByGroupCache = new HashMap<>();

        for (int index = 0; index < rows.size(); index++) {
            MatchImportRowRequest row = rows.get(index);
            int rowIndex = index + 1;

            ValidationResult validationResult = validateRow(row);
            if (!validationResult.valid()) {
                failedRows.add(
                    new MatchImportFailedRowResponse(
                        rowIndex,
                        validationResult.matchCode(),
                        validationResult.reason()
                    )
                );
                continue;
            }

            try {
                Group group = ensureGroup(validationResult.groupId(), groupCache);
                Map<String, List<Player>> playersByNickname = playersByGroupCache.computeIfAbsent(
                    validationResult.groupId(),
                    this::indexPlayersByNickname
                );

                ResolutionResult resolutionResult = resolvePlayers(
                    playersByNickname,
                    validationResult.homeTeam(),
                    validationResult.awayTeam()
                );
                if (!resolutionResult.valid()) {
                    failedRows.add(
                        new MatchImportFailedRowResponse(
                            rowIndex,
                            validationResult.matchCode(),
                            resolutionResult.reason()
                        )
                    );
                    continue;
                }

                Match match = new Match();
                match.setGroup(group);
                match.setPlayedAt(validationResult.playedAt());
                match.setWinningTeam(null);
                Match savedMatch = matchRepository.save(match);

                List<MatchParticipant> participants = new ArrayList<>(6);
                for (Player homePlayer : resolutionResult.homePlayers()) {
                    participants.add(createParticipant(savedMatch, homePlayer, TEAM_HOME));
                }
                for (Player awayPlayer : resolutionResult.awayPlayers()) {
                    participants.add(createParticipant(savedMatch, awayPlayer, TEAM_AWAY));
                }

                matchParticipantRepository.saveAll(participants);

                if (validationResult.winnerTeam() != null) {
                    matchResultService.processMatchResult(
                        savedMatch.getId(),
                        new MatchResultRequest(validationResult.winnerTeam())
                    );
                }

                importedCount++;
            } catch (Exception exception) {
                failedRows.add(
                    new MatchImportFailedRowResponse(
                        rowIndex,
                        validationResult.matchCode(),
                        "Unexpected error: " + safeTrim(exception.getMessage())
                    )
                );
            }
        }

        return new MatchImportResponse(
            rows.size(),
            importedCount,
            failedRows.size(),
            failedRows
        );
    }

    private ValidationResult validateRow(MatchImportRowRequest row) {
        if (row == null) {
            return ValidationResult.invalid(null, "Row is null");
        }

        Long groupId = row.groupId();
        if (groupId == null || groupId <= 0) {
            return ValidationResult.invalid(row.matchCode(), "groupId must be a positive number");
        }

        NormalizedTeams normalizedTeams = normalizeTeams(row.homeTeam(), row.awayTeam());
        if (!normalizedTeams.valid()) {
            return ValidationResult.invalid(row.matchCode(), normalizedTeams.reason());
        }

        String winnerTeam = normalizeWinnerTeam(row.winnerTeam());
        if (winnerTeam == null && safeTrim(row.winnerTeam()).length() > 0) {
            return ValidationResult.invalid(row.matchCode(), "winnerTeam must be HOME or AWAY");
        }

        OffsetDateTime playedAt = parsePlayedAt(row.playedAt());
        if (playedAt == null) {
            return ValidationResult.invalid(
                row.matchCode(),
                "playedAt must be ISO date/time or yyyy-MM-dd format"
            );
        }

        return ValidationResult.valid(
            groupId,
            safeTrimToNull(row.matchCode()),
            playedAt,
            normalizedTeams.homeTeam(),
            normalizedTeams.awayTeam(),
            winnerTeam
        );
    }

    private NormalizedTeams normalizeTeams(List<String> rawHomeTeam, List<String> rawAwayTeam) {
        List<String> homeTeam = normalizeNicknameList(rawHomeTeam);
        List<String> awayTeam = normalizeNicknameList(rawAwayTeam);

        if (homeTeam.size() != 3 || awayTeam.size() != 3) {
            return NormalizedTeams.invalid("homeTeam and awayTeam must each contain exactly 3 nicknames");
        }

        Set<String> allNicknames = new LinkedHashSet<>();
        allNicknames.addAll(homeTeam);
        allNicknames.addAll(awayTeam);
        if (allNicknames.size() != 6) {
            return NormalizedTeams.invalid("Players must be unique across both teams");
        }

        return NormalizedTeams.valid(homeTeam, awayTeam);
    }

    private List<String> normalizeNicknameList(List<String> nicknames) {
        if (nicknames == null) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String nickname : nicknames) {
            String trimmed = safeTrim(nickname);
            if (trimmed.isEmpty()) {
                continue;
            }
            normalized.add(trimmed);
        }
        return normalized;
    }

    private String normalizeWinnerTeam(String winnerTeam) {
        String normalized = safeTrim(winnerTeam).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        return switch (normalized) {
            case "HOME", "H" -> TEAM_HOME;
            case "AWAY", "A" -> TEAM_AWAY;
            default -> null;
        };
    }

    private OffsetDateTime parsePlayedAt(String playedAt) {
        String value = safeTrim(playedAt);
        if (value.isEmpty()) {
            return OffsetDateTime.now();
        }

        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(value).atOffset(DEFAULT_OFFSET);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDate.parse(value).atStartOfDay().atOffset(DEFAULT_OFFSET);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Group ensureGroup(Long groupId, Map<Long, Group> groupCache) {
        return groupCache.computeIfAbsent(groupId, id ->
            groupRepository.findById(id).orElseGet(() -> {
                Group group = new Group();
                group.setId(id);
                group.setName("Group " + id);
                return groupRepository.save(group);
            })
        );
    }

    private Map<String, List<Player>> indexPlayersByNickname(Long groupId) {
        List<Player> players = playerRepository.findByGroup_IdOrderByMmrDescIdAsc(groupId);
        Map<String, List<Player>> indexed = new HashMap<>();
        for (Player player : players) {
            String key = safeTrim(player.getNickname()).toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                continue;
            }
            indexed.computeIfAbsent(key, ignored -> new ArrayList<>()).add(player);
        }
        return indexed;
    }

    private ResolutionResult resolvePlayers(
        Map<String, List<Player>> playersByNickname,
        List<String> homeTeam,
        List<String> awayTeam
    ) {
        List<Player> resolvedHome = new ArrayList<>();
        List<Player> resolvedAway = new ArrayList<>();

        for (String homeNickname : homeTeam) {
            Player player = resolvePlayer(playersByNickname, homeNickname);
            if (player == null) {
                return ResolutionResult.invalid("Player not found: " + homeNickname);
            }
            if (player.getId() == null) {
                return ResolutionResult.invalid("Player ID is missing: " + homeNickname);
            }
            resolvedHome.add(player);
        }

        for (String awayNickname : awayTeam) {
            Player player = resolvePlayer(playersByNickname, awayNickname);
            if (player == null) {
                return ResolutionResult.invalid("Player not found: " + awayNickname);
            }
            if (player.getId() == null) {
                return ResolutionResult.invalid("Player ID is missing: " + awayNickname);
            }
            resolvedAway.add(player);
        }

        Set<Long> uniqueIds = new LinkedHashSet<>();
        resolvedHome.forEach(player -> uniqueIds.add(player.getId()));
        resolvedAway.forEach(player -> uniqueIds.add(player.getId()));
        if (uniqueIds.size() != 6) {
            return ResolutionResult.invalid("Players must be unique across both teams");
        }

        return ResolutionResult.valid(resolvedHome, resolvedAway);
    }

    private Player resolvePlayer(Map<String, List<Player>> playersByNickname, String nickname) {
        String key = safeTrim(nickname).toLowerCase(Locale.ROOT);
        List<Player> matched = playersByNickname.get(key);
        if (matched == null || matched.isEmpty()) {
            return null;
        }
        if (matched.size() > 1) {
            return null;
        }
        return matched.get(0);
    }

    private MatchParticipant createParticipant(Match match, Player player, String team) {
        MatchParticipant participant = new MatchParticipant();
        participant.setMatch(match);
        participant.setPlayer(player);
        participant.setTeam(team);

        int mmr = player.getMmr() == null ? 0 : player.getMmr();
        participant.setMmrBefore(mmr);
        participant.setMmrAfter(mmr);
        participant.setMmrDelta(0);
        return participant;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeTrimToNull(String value) {
        String trimmed = safeTrim(value);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ValidationResult(
        boolean valid,
        Long groupId,
        String matchCode,
        OffsetDateTime playedAt,
        List<String> homeTeam,
        List<String> awayTeam,
        String winnerTeam,
        String reason
    ) {

        private static ValidationResult valid(
            Long groupId,
            String matchCode,
            OffsetDateTime playedAt,
            List<String> homeTeam,
            List<String> awayTeam,
            String winnerTeam
        ) {
            return new ValidationResult(
                true,
                groupId,
                matchCode,
                playedAt,
                homeTeam,
                awayTeam,
                winnerTeam,
                null
            );
        }

        private static ValidationResult invalid(String matchCode, String reason) {
            return new ValidationResult(
                false,
                null,
                safeMatchCode(matchCode),
                null,
                List.of(),
                List.of(),
                null,
                reason
            );
        }

        private static String safeMatchCode(String matchCode) {
            if (matchCode == null) {
                return null;
            }
            String trimmed = matchCode.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }

    private record NormalizedTeams(
        boolean valid,
        List<String> homeTeam,
        List<String> awayTeam,
        String reason
    ) {
        private static NormalizedTeams valid(List<String> homeTeam, List<String> awayTeam) {
            return new NormalizedTeams(true, homeTeam, awayTeam, null);
        }

        private static NormalizedTeams invalid(String reason) {
            return new NormalizedTeams(false, List.of(), List.of(), reason);
        }
    }

    private record ResolutionResult(
        boolean valid,
        List<Player> homePlayers,
        List<Player> awayPlayers,
        String reason
    ) {
        private static ResolutionResult valid(List<Player> homePlayers, List<Player> awayPlayers) {
            return new ResolutionResult(true, homePlayers, awayPlayers, null);
        }

        private static ResolutionResult invalid(String reason) {
            return new ResolutionResult(false, List.of(), List.of(), reason);
        }
    }
}
