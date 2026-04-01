package com.balancify.backend.repository;

import com.balancify.backend.domain.Player;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByGroup_IdOrderByMmrDescIdAsc(Long groupId);

    List<Player> findByGroup_IdAndNicknameIgnoreCase(Long groupId, String nickname);

    Optional<Player> findByIdAndGroup_Id(Long playerId, Long groupId);

    List<Player> findByGroup_IdAndIdIn(Long groupId, List<Long> playerIds);
}
