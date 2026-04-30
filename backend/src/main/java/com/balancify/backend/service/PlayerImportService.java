package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupPlayerImportFailedRowResponse;
import com.balancify.backend.api.group.dto.GroupPlayerImportRequest;
import com.balancify.backend.api.group.dto.GroupPlayerImportResponse;
import com.balancify.backend.api.group.dto.GroupPlayerImportRowRequest;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerImportService {

    private static final String REASSIGNMENT_TIER = "재배정대상";
    private static final Map<String, Integer> DEFAULT_BASE_MMR_BY_TIER = defaultBaseMmrByTier();

    private final PlayerRepository playerRepository;
    private final GroupRepository groupRepository;

    public PlayerImportService(
        PlayerRepository playerRepository,
        GroupRepository groupRepository
    ) {
        this.playerRepository = playerRepository;
        this.groupRepository = groupRepository;
    }

    @Transactional
    public GroupPlayerImportResponse importPlayers(Long groupId, GroupPlayerImportRequest request) {
        List<GroupPlayerImportRowRequest> rows =
            request == null || request.players() == null ? List.of() : request.players();

        Group group = ensureGroup(groupId);

        int createdCount = 0;
        int updatedCount = 0;
        List<GroupPlayerImportFailedRowResponse> failedRows = new ArrayList<>();
        Set<String> payloadNicknames = new HashSet<>();

        for (GroupPlayerImportRowRequest row : rows) {
            ValidationResult validationResult = validateRow(row, payloadNicknames);
            if (!validationResult.valid()) {
                failedRows.add(
                    new GroupPlayerImportFailedRowResponse(
                        validationResult.nickname(),
                        validationResult.reason()
                    )
                );
                continue;
            }

            List<Player> matchedPlayers =
                playerRepository.findByGroup_IdAndNicknameIgnoreCase(groupId, validationResult.nickname());
            Player player = matchedPlayers.isEmpty() ? new Player() : matchedPlayers.get(0);
            boolean isCreate = matchedPlayers.isEmpty();

            if (isCreate) {
                player.setGroup(group);
            }

            player.setNickname(validationResult.nickname());
            player.setTier(validationResult.tier());
            player.setBaseMmr(validationResult.baseMmr());
            player.setMmr(validationResult.currentMmr());
            player.setNote(validationResult.note());
            if (validationResult.race() != null) {
                player.setRace(validationResult.race());
            } else if (isCreate && (player.getRace() == null || player.getRace().isBlank())) {
                player.setRace("P");
            }

            playerRepository.save(player);

            if (isCreate) {
                createdCount++;
            } else {
                updatedCount++;
            }
        }

        return new GroupPlayerImportResponse(
            rows.size(),
            createdCount,
            updatedCount,
            failedRows.size(),
            failedRows
        );
    }

    private Group ensureGroup(Long groupId) {
        return groupRepository.findById(groupId).orElseGet(() -> {
            Group group = new Group();
            group.setId(groupId);
            group.setName("Group " + groupId);
            return groupRepository.save(group);
        });
    }

    private ValidationResult validateRow(
        GroupPlayerImportRowRequest row,
        Set<String> payloadNicknames
    ) {
        if (row == null) {
            return ValidationResult.invalid("", "Row is null");
        }

        String nickname = safeTrim(row.nickname());
        if (nickname.isEmpty()) {
            return ValidationResult.invalid("", "Nickname is required");
        }

        String nicknameKey = nickname.toLowerCase(Locale.ROOT);
        if (!payloadNicknames.add(nicknameKey)) {
            return ValidationResult.invalid(nickname, "Duplicate nickname in payload");
        }

        String tier = safeTrim(row.tier());
        if (tier.isEmpty()) {
            return ValidationResult.invalid(nickname, "Tier is required");
        }

        tier = normalizeTier(tier);
        String race = normalizeRace(row.race());
        if (race == null && row.race() != null && !row.race().trim().isEmpty()) {
            return ValidationResult.invalid(nickname, "Race must be one of P,T,Z,PT,PZ,TZ,PTZ");
        }
        boolean reassignmentTier = REASSIGNMENT_TIER.equals(tier);
        Integer baseMmr = row.baseMmr();
        Integer currentMmr = row.currentMmr();

        int normalizedBaseMmr;
        if (reassignmentTier) {
            normalizedBaseMmr = baseMmr == null || baseMmr <= 0 ? 0 : baseMmr;
        } else if (baseMmr != null && baseMmr > 0) {
            normalizedBaseMmr = baseMmr;
        } else {
            Integer defaultBaseMmr = DEFAULT_BASE_MMR_BY_TIER.get(tier.toUpperCase(Locale.ROOT));
            if (defaultBaseMmr == null) {
                return ValidationResult.invalid(
                    nickname,
                    "baseMmr is required unless tier has a default MMR mapping"
                );
            }
            normalizedBaseMmr = defaultBaseMmr;
        }

        if (normalizedBaseMmr < 0) {
            return ValidationResult.invalid(
                nickname,
                "baseMmr must be zero or positive"
            );
        }

        int normalizedCurrentMmr = currentMmr == null ? normalizedBaseMmr : currentMmr;

        if (normalizedCurrentMmr < 0) {
            return ValidationResult.invalid(nickname, "currentMmr must be zero or positive");
        }

        return ValidationResult.valid(
            nickname,
            tier,
            normalizedBaseMmr,
            normalizedCurrentMmr,
            race,
            reassignmentTier ? row.note() : trimToNull(row.note())
        );
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeTier(String tier) {
        if (REASSIGNMENT_TIER.equals(tier)) {
            return REASSIGNMENT_TIER;
        }

        String uppercaseTier = tier.toUpperCase(Locale.ROOT);
        if (DEFAULT_BASE_MMR_BY_TIER.containsKey(uppercaseTier)) {
            return uppercaseTier;
        }

        return tier;
    }

    private String normalizeRace(String race) {
        String normalizedRace = safeTrim(race);
        if (normalizedRace.isEmpty()) {
            return null;
        }
        try {
            return PlayerRacePolicy.normalizeCapability(normalizedRace.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Map<String, Integer> defaultBaseMmrByTier() {
        Map<String, Integer> mapping = new HashMap<>();
        mapping.put("S", 2000);
        mapping.put("A+", 1800);
        mapping.put("A", 1600);
        mapping.put("A-", 1400);
        mapping.put("B+", 1200);
        mapping.put("B", 1000);
        mapping.put("B-", 800);
        mapping.put("C+", 600);
        mapping.put("C", 400);
        mapping.put("C-", 200);
        return Map.copyOf(mapping);
    }

    private record ValidationResult(
        boolean valid,
        String nickname,
        String tier,
        int baseMmr,
        int currentMmr,
        String race,
        String note,
        String reason
    ) {

        private static ValidationResult valid(
            String nickname,
            String tier,
            int baseMmr,
            int currentMmr,
            String race,
            String note
        ) {
            return new ValidationResult(true, nickname, tier, baseMmr, currentMmr, race, note, null);
        }

        private static ValidationResult invalid(String nickname, String reason) {
            return new ValidationResult(false, nickname, null, 0, 0, null, null, reason);
        }
    }
}
