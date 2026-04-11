package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupPlayerUpdateRequest;
import com.balancify.backend.api.group.dto.GroupPlayerMmrUpdateRequest;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.PlayerRepository;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerAdminService {

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
        String normalizedRace = race.isEmpty() ? "" : race.toUpperCase(Locale.ROOT);

        if (nickname.isEmpty() && normalizedRace.isEmpty() && active == null) {
            throw new IllegalArgumentException("At least one field is required");
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
            player.setActive(active);
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
}
