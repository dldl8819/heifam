package com.balancify.backend.repository;

import com.balancify.backend.domain.Player;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByGroup_IdOrderByMmrDescIdAsc(Long groupId);

    List<Player> findByGroup_IdAndNicknameIgnoreCase(Long groupId, String nickname);

    List<Player> findByNicknameIgnoreCaseAndAnonymizedAtIsNull(String nickname);

    List<Player> findByAuthUserIdAndAnonymizedAtIsNull(UUID authUserId);

    Optional<Player> findByIdAndGroup_Id(Long playerId, Long groupId);

    List<Player> findByGroup_IdAndIdIn(Long groupId, List<Long> playerIds);
}
