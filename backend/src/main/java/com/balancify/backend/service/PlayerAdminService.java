package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupPlayerUpdateRequest;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.PlayerRepository;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerAdminService {

    private static final Set<String> ALLOWED_RACES = Set.of("P", "T", "Z", "PT", "PZ", "TZ", "R");

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
        String normalizedRace = race.isEmpty() ? "" : race.toUpperCase(Locale.ROOT);

        if (nickname.isEmpty() && normalizedRace.isEmpty()) {
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
            if (!ALLOWED_RACES.contains(normalizedRace)) {
                throw new IllegalArgumentException("Race must be one of P,T,Z,PT,PZ,TZ,R");
            }
            player.setRace(normalizedRace);
        }

        playerRepository.save(player);
    }

    @Transactional
    public void deletePlayer(Long groupId, Long playerId) {
        Player player = playerRepository.findByIdAndGroup_Id(playerId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Player not found"));
        playerRepository.delete(player);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
