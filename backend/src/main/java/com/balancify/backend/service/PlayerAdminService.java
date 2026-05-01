package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupPlayerUpdateRequest;
import com.balancify.backend.api.group.dto.GroupPlayerMmrUpdateRequest;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.PlayerRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerAdminService {

    private static final int CHAT_LEFT_REASON_MAX_LENGTH = 500;
    private static final Set<String> ACKNOWLEDGEABLE_TIERS = Set.of(
        "S", "A+", "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "UNASSIGNED"
    );

    private final PlayerRepository playerRepository;

    public PlayerAdminService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Transactional
    public void updatePlayer(
        Long groupId,
        Long playerId,
        GroupPlayerUpdateRequest request
    ) {
        Player player = playerRepository.findByIdAndGroup_Id(playerId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Player not found"));

        String nickname = safeTrim(request == null ? null : request.nickname());
        String race = safeTrim(request == null ? null : request.race());
        Boolean active = request == null ? null : request.active();
        OffsetDateTime chatLeftAt = request == null ? null : request.chatLeftAt();
        String chatLeftReason = safeTrim(request == null ? null : request.chatLeftReason());
        OffsetDateTime chatRejoinedAt = request == null ? null : request.chatRejoinedAt();
        String tierChangeAcknowledgedTier =
            normalizeTierChangeAcknowledgement(request == null ? null : request.tierChangeAcknowledgedTier());
        String normalizedRace = race.isEmpty() ? "" : race.toUpperCase(Locale.ROOT);

        if (
            nickname.isEmpty()
                && normalizedRace.isEmpty()
                && active == null
                && chatLeftAt == null
                && chatLeftReason.isEmpty()
                && chatRejoinedAt == null
                && tierChangeAcknowledgedTier.isEmpty()
        ) {
            throw new IllegalArgumentException("At least one field is required");
        }

        boolean hasChatMetadata = chatLeftAt != null || !chatLeftReason.isEmpty() || chatRejoinedAt != null;
        if (active == null && hasChatMetadata) {
            throw new IllegalArgumentException("Chat activity metadata requires active status change");
        }

        if (!nickname.isEmpty()) {
            List<Player> sameNicknamePlayers =
                playerRepository.findByGroup_IdAndNicknameIgnoreCase(groupId, nickname);
            boolean duplicateExists = sameNicknamePlayers
                .stream()
                .anyMatch(found -> found.getId() != null && !found.getId().equals(playerId));
            if (duplicateExists) {
                throw new IllegalArgumentException("Nickname already exists in group");
            }

            player.setNickname(nickname);
        }

        if (!normalizedRace.isEmpty()) {
            try {
                player.setRace(PlayerRacePolicy.normalizeCapability(normalizedRace));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Race must be one of P,T,Z,PT,PZ,TZ,PTZ");
            }
        }

        if (active != null) {
            if (active) {
                if (chatRejoinedAt == null) {
                    throw new IllegalArgumentException("Chat rejoined time is required when reactivating player");
                }
                player.setActive(true);
                player.setChatRejoinedAt(chatRejoinedAt);
            } else {
                if (chatLeftAt == null) {
                    throw new IllegalArgumentException("Chat left time is required when deactivating player");
                }
                if (chatLeftReason.isEmpty()) {
                    throw new IllegalArgumentException("Chat left reason is required when deactivating player");
                }
                if (chatLeftReason.length() > CHAT_LEFT_REASON_MAX_LENGTH) {
                    throw new IllegalArgumentException("Chat left reason must be 500 characters or fewer");
                }
                player.setActive(false);
                player.setChatLeftAt(chatLeftAt);
                player.setChatLeftReason(chatLeftReason);
                player.setChatRejoinedAt(null);
            }
        }

        if (!tierChangeAcknowledgedTier.isEmpty()) {
            player.setTierChangeAcknowledgedTier(tierChangeAcknowledgedTier);
            player.setTierChangeAcknowledgedAt(OffsetDateTime.now());
        }

        playerRepository.save(player);
    }

    @Transactional
    public void updatePlayerMmr(
        Long groupId,
        Long playerId,
        GroupPlayerMmrUpdateRequest request
    ) {
        Player player = playerRepository.findByIdAndGroup_Id(playerId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Player not found"));

        Integer nextMmr = request == null ? null : request.mmr();
        if (nextMmr == null) {
            throw new IllegalArgumentException("MMR is required");
        }
        if (nextMmr < 0 || nextMmr > 5000) {
            throw new IllegalArgumentException("MMR must be between 0 and 5000");
        }

        player.setMmr(nextMmr);
        playerRepository.save(player);
    }

    @Transactional
    public void deletePlayer(Long groupId, Long playerId) {
        Player player = playerRepository.findByIdAndGroup_Id(playerId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Player not found"));
        try {
            playerRepository.delete(player);
            playerRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException(
                "매치 또는 드래프트 기록이 남아 있는 선수는 삭제할 수 없습니다.",
                exception
            );
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeTierChangeAcknowledgement(String value) {
        String normalized = safeTrim(value).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        if ("NONE".equals(normalized) || "PENDING".equals(normalized) || "TBD".equals(normalized)) {
            return "UNASSIGNED";
        }
        if (!ACKNOWLEDGEABLE_TIERS.contains(normalized)) {
            throw new IllegalArgumentException("Tier change acknowledgement tier is invalid");
        }
        return normalized;
    }
}
